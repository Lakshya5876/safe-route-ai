#!/usr/bin/env node
/**
 * Stage 1: fetch raw OpenStreetMap features for one or all supported cities via
 * the Overpass API (https://overpass-api.de), a free, live, no-auth-required
 * query API over OSM's real, crowd-maintained map data (ODbL license —
 * attribution required, see pipeline/README.md).
 *
 * This replaces the old hand-typed 16-row CSV of fake "risk zones" with a
 * real, per-city, machine-fetched dataset. It pulls three tag families that
 * are defensible proxies for the "isolated / poorly-lit segment" risk signal
 * the original project README named but never implemented:
 *   - highway ways explicitly tagged lit=no          (poor lighting)
 *   - highway=track|path|bridleway ways              (isolation proxy)
 *   - amenity=police|hospital nodes                  (protective presence)
 *
 * The public Overpass instance is shared infrastructure and does rate-limit /
 * time out under load (observed directly during development). This script
 * retries with backoff and falls back to a second mirror before giving up —
 * and if both fail, it exits non-zero WITHOUT touching any existing cached
 * file, so a transient network failure can never silently replace good data
 * with empty data (the "malformed dataset must never silently replace a valid
 * dataset" requirement).
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { CITIES, CITY_IDS, getCity } from "./cities.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW_DIR = path.join(__dirname, "..", "data", "raw");

const MIRRORS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
];

const QUERY_TIMEOUT_S = 60;
const FETCH_TIMEOUT_MS = 90_000;
const MAX_ATTEMPTS_PER_MIRROR = 3;
const RETRY_BACKOFF_MS = 15000;

function buildQuery(bbox) {
  const [s, w, n, e] = bbox;
  const box = `${s},${w},${n},${e}`;
  return `[out:json][timeout:${QUERY_TIMEOUT_S}];
(
  way["highway"]["lit"="no"](${box});
  way["highway"~"^(track|path|bridleway)$"](${box});
  node["amenity"~"^(police|hospital)$"](${box});
);
out center;`;
}

async function fetchWithTimeout(url, body, timeoutMs) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "application/json, text/html;q=0.5, */*;q=0.1",
        // Overpass's usage policy asks clients to identify themselves.
        "User-Agent": "SafeRouteAI-Pipeline/1.0 (local dev, non-commercial research use)",
      },
      body: new URLSearchParams({ data: body }),
      signal: controller.signal,
    });
    const text = await res.text();
    return { ok: res.ok, status: res.status, text };
  } finally {
    clearTimeout(t);
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** Fetch one city, trying each mirror with retries. Throws on total failure. */
async function fetchCity(city) {
  const query = buildQuery(city.bbox);
  const errors = [];

  for (const mirror of MIRRORS) {
    for (let attempt = 1; attempt <= MAX_ATTEMPTS_PER_MIRROR; attempt++) {
      try {
        const { ok, status, text } = await fetchWithTimeout(
          mirror,
          query,
          FETCH_TIMEOUT_MS
        );
        if (!ok) {
          errors.push(`${mirror} attempt ${attempt}: HTTP ${status}`);
          await sleep(RETRY_BACKOFF_MS);
          continue;
        }
        let json;
        try {
          json = JSON.parse(text);
        } catch {
          errors.push(
            `${mirror} attempt ${attempt}: non-JSON response (likely an Overpass error page, first 120 chars: ${text
              .slice(0, 120)
              .replace(/\s+/g, " ")})`
          );
          await sleep(RETRY_BACKOFF_MS);
          continue;
        }
        if (!Array.isArray(json.elements)) {
          errors.push(`${mirror} attempt ${attempt}: JSON had no elements[]`);
          await sleep(RETRY_BACKOFF_MS);
          continue;
        }
        return { json, mirror, attempts: errors.length + 1 };
      } catch (e) {
        errors.push(`${mirror} attempt ${attempt}: ${e.message}`);
        await sleep(RETRY_BACKOFF_MS);
      }
    }
  }
  throw new Error(
    `All Overpass mirrors failed for ${city.name}:\n  ` + errors.join("\n  ")
  );
}

export async function fetchAndCacheCity(cityId) {
  const city = getCity(cityId);
  console.log(`[fetch-osm] ${city.name}: querying Overpass (bbox=${city.bbox.join(",")})...`);
  const { json, mirror, attempts } = await fetchCity(city);

  const outDir = RAW_DIR;
  fs.mkdirSync(outDir, { recursive: true });

  const fetchedAt = new Date().toISOString();
  const osmBaseTimestamp = json.osm3s?.timestamp_osm_base ?? null;

  const record = {
    cityId: city.id,
    cityName: city.name,
    bbox: city.bbox,
    source: "OpenStreetMap via Overpass API",
    sourceMirrorUsed: mirror,
    attemptsNeeded: attempts,
    fetchedAt,
    osmBaseTimestamp,
    license: "ODbL (c) OpenStreetMap contributors",
    elementCount: json.elements.length,
    elements: json.elements,
  };

  const versionedPath = path.join(
    outDir,
    `${city.id}-osm-${fetchedAt.replace(/[:.]/g, "-")}.json`
  );
  const latestPath = path.join(outDir, `${city.id}-osm-latest.json`);

  fs.writeFileSync(versionedPath, JSON.stringify(record, null, 2));
  fs.writeFileSync(latestPath, JSON.stringify(record, null, 2));

  console.log(
    `[fetch-osm] ${city.name}: OK — ${json.elements.length} elements from ${mirror} ` +
      `(${attempts} attempt${attempts > 1 ? "s" : ""}). Cached: ${path.basename(versionedPath)}`
  );
  return record;
}

async function main() {
  const arg = process.argv[2];
  const targets = arg && arg !== "all" ? [arg] : CITY_IDS;
  const results = { ok: [], failed: [] };

  for (const cityId of targets) {
    try {
      const r = await fetchAndCacheCity(cityId);
      results.ok.push({ city: cityId, elementCount: r.elementCount });
    } catch (e) {
      console.error(`[fetch-osm] ${cityId}: FAILED — ${e.message}`);
      console.error(
        `[fetch-osm] ${cityId}: leaving any previously cached data untouched.`
      );
      results.failed.push({ city: cityId, error: e.message });
    }
  }

  console.log("\n[fetch-osm] Summary:");
  console.log(`  succeeded: ${results.ok.map((r) => `${r.city}(${r.elementCount})`).join(", ") || "none"}`);
  console.log(`  failed:    ${results.failed.map((r) => r.city).join(", ") || "none"}`);

  if (results.failed.length > 0 && results.ok.length === 0) {
    process.exit(1);
  }
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, "/")}`) {
  main();
}
