#!/usr/bin/env node
/**
 * Stage 2: raw source data -> canonical SafetyFactor[] (see canonical-schema.js).
 *
 * Two adapters feed this stage today:
 *  - osmToCandidates(): unpacks the raw Overpass element list cached by
 *    fetch-osm-features.js into candidate records.
 *  - cityPriorToCandidate(): turns a city's single NCRB-derived crime prior
 *    (see cities.js) into one city_aggregate candidate record.
 *
 * Every candidate is run through validateSafetyFactor() before being
 * accepted. Rejected candidates are counted and the reasons logged in the
 * output manifest — nothing is silently dropped without a trace, and this
 * script never writes an output file if the accepted count is suspiciously
 * low relative to the input (see MIN_ACCEPT_RATIO) so a schema regression or
 * a bad OSM response can't quietly replace a healthy canonical dataset.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { CITIES, CITY_IDS, getCity } from "./cities.js";
import { validateSafetyFactor } from "./canonical-schema.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW_DIR = path.join(__dirname, "..", "data", "raw");
const CANONICAL_DIR = path.join(__dirname, "..", "data", "canonical");

const PIPELINE_VERSION = "canonical-v1";

// Base severity weight per OSM tag pattern. Documented rationale, not
// arbitrary: lit=no on a highway is a direct, verifiable statement about
// the physical environment (highest weight among the OSM-derived signals).
// track/path/bridleway is a weaker proxy for isolation (unpaved/informal
// routes correlate with lower footfall and oversight, but plenty are
// perfectly safe parkland trails) so it gets a lower base weight and lower
// input confidence. Police presence is a stronger protective signal than a
// hospital (active deterrence + patrol proximity vs. passive emergency
// response), so it is weighted higher.
const OSM_WEIGHTS = {
  poor_lighting: { weight: 0.6, confidence: 0.85 },
  isolation: { weight: 0.4, confidence: 0.7 },
  protective_police: { weight: 0.8, confidence: 0.9 },
  protective_hospital: { weight: 0.5, confidence: 0.9 },
};

const MIN_ACCEPT_RATIO = 0.5; // refuse to publish if >50% of candidates are rejected

function elementLatLon(el) {
  if (el.type === "node" && Number.isFinite(el.lat) && Number.isFinite(el.lon)) {
    return [el.lat, el.lon];
  }
  if (el.center && Number.isFinite(el.center.lat) && Number.isFinite(el.center.lon)) {
    return [el.center.lat, el.center.lon];
  }
  return null;
}

export function osmToCandidates(osmRecord) {
  const candidates = [];
  const datasetVersion = `osm-${osmRecord.osmBaseTimestamp ?? osmRecord.fetchedAt}`;

  for (const el of osmRecord.elements) {
    const latlon = elementLatLon(el);
    if (!latlon) continue;
    const [lat, lon] = latlon;
    const tags = el.tags || {};
    const base = {
      lat,
      lon,
      spatialPrecision: "point",
      source: "OpenStreetMap via Overpass API",
      sourceUrl: "https://www.openstreetmap.org/copyright",
      datasetVersion,
      ingestedAt: osmRecord.fetchedAt,
    };

    if (tags.highway && tags.lit === "no") {
      const w = OSM_WEIGHTS.poor_lighting;
      candidates.push({ ...base, category: "poor_lighting", polarity: "risk", ...w });
    } else if (tags.highway && ["track", "path", "bridleway"].includes(tags.highway)) {
      const w = OSM_WEIGHTS.isolation;
      candidates.push({ ...base, category: "isolation", polarity: "risk", ...w });
    } else if (tags.amenity === "police") {
      const w = OSM_WEIGHTS.protective_police;
      candidates.push({ ...base, category: "protective_presence", polarity: "protective", ...w });
    } else if (tags.amenity === "hospital") {
      const w = OSM_WEIGHTS.protective_hospital;
      candidates.push({ ...base, category: "protective_presence", polarity: "protective", ...w });
    }
    // Any other element shape is silently out of scope for this adapter (not
    // a rejection — we didn't ask Overpass for it, so it can't be "invalid").
  }
  return candidates;
}

export function cityPriorToCandidate(city) {
  return {
    lat: city.center[0],
    lon: city.center[1],
    category: "crime_prior",
    polarity: "risk",
    weight: city.crimePrior.value,
    spatialPrecision: "city_aggregate",
    source: city.crimePrior.source,
    sourceUrl: city.crimePrior.sourceUrl,
    datasetVersion: "ncrb-2022-snapshot",
    ingestedAt: new Date().toISOString(),
    confidence: city.crimePrior.verified ? 0.6 : 0.25,
  };
}

export function buildCanonicalForCity(cityId) {
  const city = getCity(cityId);
  const rawPath = path.join(RAW_DIR, `${city.id}-osm-latest.json`);
  if (!fs.existsSync(rawPath)) {
    throw new Error(
      `No raw OSM cache for ${city.name} at ${rawPath}. Run fetch-osm-features.js first.`
    );
  }
  const osmRecord = JSON.parse(fs.readFileSync(rawPath, "utf8"));

  const candidates = [
    ...osmToCandidates(osmRecord),
    cityPriorToCandidate(city),
  ];

  const accepted = [];
  const rejected = [];
  for (const c of candidates) {
    const result = validateSafetyFactor(c);
    if (result.ok) accepted.push(result.event);
    else rejected.push({ reason: result.reason, record: c });
  }

  const acceptRatio = candidates.length > 0 ? accepted.length / candidates.length : 0;
  if (candidates.length > 0 && acceptRatio < MIN_ACCEPT_RATIO) {
    throw new Error(
      `${city.name}: only ${(acceptRatio * 100).toFixed(1)}% of ${candidates.length} candidates ` +
        `passed validation (threshold ${MIN_ACCEPT_RATIO * 100}%). Refusing to publish — this ` +
        `usually means an adapter bug, not bad source data. First rejection: ${
          rejected[0]?.reason ?? "n/a"
        }`
    );
  }

  const manifest = {
    cityId: city.id,
    pipelineVersion: PIPELINE_VERSION,
    generatedAt: new Date().toISOString(),
    inputRecordCount: candidates.length,
    acceptedCount: accepted.length,
    rejectedCount: rejected.length,
    rejectionReasons: summarizeRejections(rejected),
    categoryBreakdown: countBy(accepted, (e) => e.category),
    osmSourceFetchedAt: osmRecord.fetchedAt,
    osmSourceMirror: osmRecord.sourceMirrorUsed,
  };

  fs.mkdirSync(CANONICAL_DIR, { recursive: true });
  const outPath = path.join(CANONICAL_DIR, `${city.id}-events.json`);
  fs.writeFileSync(outPath, JSON.stringify({ manifest, events: accepted }, null, 2));

  console.log(
    `[build-canonical] ${city.name}: accepted ${accepted.length}/${candidates.length} ` +
      `(rejected ${rejected.length}). Wrote ${path.basename(outPath)}`
  );
  return { manifest, events: accepted };
}

function summarizeRejections(rejected) {
  const counts = {};
  for (const r of rejected) counts[r.reason] = (counts[r.reason] || 0) + 1;
  return counts;
}

function countBy(arr, fn) {
  const out = {};
  for (const x of arr) {
    const k = fn(x);
    out[k] = (out[k] || 0) + 1;
  }
  return out;
}

async function main() {
  const arg = process.argv[2];
  const targets = arg && arg !== "all" ? [arg] : CITY_IDS;
  let anyFailed = false;
  for (const cityId of targets) {
    try {
      buildCanonicalForCity(cityId);
    } catch (e) {
      console.error(`[build-canonical] ${cityId}: FAILED — ${e.message}`);
      anyFailed = true;
    }
  }
  if (anyFailed) process.exit(1);
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, "/")}`) {
  main();
}
