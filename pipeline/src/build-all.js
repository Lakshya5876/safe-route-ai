#!/usr/bin/env node
/**
 * Orchestrates the full reproducible pipeline for one or all cities:
 *   fetch-osm-features -> build-canonical-events -> build-risk-grid
 *
 * Usage: node src/build-all.js [cityId|all]
 *
 * Reproducibility: given the same OSM snapshot (the raw cache under
 * data/raw/) and the same pipeline version, build-canonical-events and
 * build-risk-grid are pure functions of their input files — re-running them
 * without re-fetching produces byte-identical output. Only fetch-osm-features
 * talks to the network, and it is the only stage whose output can legitimately
 * differ between runs (OSM data changes over time); its cache is versioned
 * and timestamped for exactly this reason.
 */
import { CITY_IDS } from "./cities.js";
import { fetchAndCacheCity } from "./fetch-osm-features.js";
import { buildCanonicalForCity } from "./build-canonical-events.js";
import { buildGridForCity } from "./build-risk-grid.js";

async function buildOne(cityId) {
  await fetchAndCacheCity(cityId);
  buildCanonicalForCity(cityId);
  buildGridForCity(cityId);
}

async function main() {
  const arg = process.argv[2];
  const targets = arg && arg !== "all" ? [arg] : CITY_IDS;
  const results = { ok: [], failed: [] };

  for (const cityId of targets) {
    console.log(`\n=== ${cityId} ===`);
    try {
      await buildOne(cityId);
      results.ok.push(cityId);
    } catch (e) {
      console.error(`[build-all] ${cityId}: FAILED — ${e.message}`);
      results.failed.push({ cityId, error: e.message });
    }
  }

  console.log("\n=== Pipeline summary ===");
  console.log(`OK:     ${results.ok.join(", ") || "none"}`);
  console.log(`FAILED: ${results.failed.map((f) => f.cityId).join(", ") || "none"}`);
  if (results.failed.length > 0) process.exit(1);
}

main();
