#!/usr/bin/env node
/**
 * Stage 3: canonical SafetyFactor[] -> a versioned per-city risk grid.
 *
 * This is the one piece of real spatial modelling in the pipeline, and it
 * directly replaces the old "16 hand-typed circles + binary intersection"
 * approach audited earlier. Design, and why:
 *
 * - The city is tiled into a regular grid of ~150m cells (approximately
 *   square in real distance — the longitude step is corrected by cos(lat)
 *   for the city's latitude). Every risk factor contributes to nearby cells
 *   through a Gaussian distance-decay kernel, not a hard-edged circle: a
 *   point 10m from an unlit road contributes almost as much as one exactly
 *   on it, and the contribution fades smoothly rather than cutting off at an
 *   arbitrary radius. This removes the old model's zone-boundary
 *   discontinuity and its "binary touch, no matter how central or glancing"
 *   flaw.
 * - Risk categories are kept SEPARATE per cell (poorLighting, isolation,
 *   crimePrior, protective) rather than pre-summed. Time-of-day and travel
 *   mode multipliers are applied at request time in the backend by
 *   recombining these four numbers — so the grid only has to be rebuilt when
 *   the underlying data changes, not when someone travels at a different
 *   hour, and segment coloring and route-level scoring both read the exact
 *   same four numbers (one coherent model, not two divergent ones).
 * - `sampleCount` / `confidence` per cell come ONLY from local point-feature
 *   density (OSM lighting/isolation/protective features within the kernel's
 *   effective radius) — the city-wide crime prior contributes to risk but,
 *   being citywide rather than local, deliberately does NOT raise a cell's
 *   confidence. A cell with no nearby OSM features stays low-confidence even
 *   though it still carries the coarse city-level risk number. This is how
 *   "no local data" avoids silently becoming "confidently safe."
 * - `referenceScale` (used to normalize raw risk into a 0-1 band) is the 95th
 *   percentile of this CITY's own cell-risk distribution, computed from only
 *   that city's cells. This is the direct fix for the previously audited bug
 *   where normalizing against a sum over every zone in every loaded city
 *   meant adding a new city silently changed every existing city's scores.
 *   Regenerating or adding another city can now never change another city's
 *   numbers, by construction.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { CITY_IDS, getCity } from "./cities.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CANONICAL_DIR = path.join(__dirname, "..", "data", "canonical");
const OUTPUT_DIR = path.join(__dirname, "..", "..", "backend", "src", "main", "resources", "risk-grids");

const PIPELINE_VERSION = "risk-grid-v1";
const CELL_METERS = 150;
const EARTH_RADIUS_M = 6371000;

// Gaussian kernel sigma (meters) per point category — how far a factor's
// influence reasonably extends. Documented, not tuned against any ground
// truth (there is none available), so treat these as a reasonable starting
// point, not a calibrated constant — see the audit's severity-weight finding.
const SIGMA_M = {
  poor_lighting: 120,
  isolation: 100,
  protective_presence: 250,
};
const CUTOFF_SIGMAS = 3;

// Confidence saturation constant: confidence(n) = 1 - exp(-K * n).
// K=0.4 => confidence(1)=0.33, (3)=0.70, (5)=0.86, (8)=0.96.
const CONFIDENCE_K = 0.4;

export function haversineMeters(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

export function buildGridForCity(cityId) {
  const city = getCity(cityId);
  const canonicalPath = path.join(CANONICAL_DIR, `${city.id}-events.json`);
  if (!fs.existsSync(canonicalPath)) {
    throw new Error(
      `No canonical events for ${city.name} at ${canonicalPath}. Run build-canonical-events.js first.`
    );
  }
  const { manifest: canonicalManifest, events } = JSON.parse(
    fs.readFileSync(canonicalPath, "utf8")
  );

  const [south, west, north, east] = city.bbox;
  const latRad = (city.center[0] * Math.PI) / 180;
  const cellLatDeg = CELL_METERS / 111320;
  const cellLonDeg = CELL_METERS / (111320 * Math.cos(latRad));

  const latCells = Math.max(1, Math.ceil((north - south) / cellLatDeg));
  const lonCells = Math.max(1, Math.ceil((east - west) / cellLonDeg));

  // cellId -> accumulator
  const cells = new Map();
  function cellFor(latIdx, lonIdx) {
    const key = `${latIdx}_${lonIdx}`;
    let cell = cells.get(key);
    if (!cell) {
      cell = {
        latIdx,
        lonIdx,
        lat: south + (latIdx + 0.5) * cellLatDeg,
        lon: west + (lonIdx + 0.5) * cellLonDeg,
        poorLighting: 0,
        isolation: 0,
        protective: 0,
        crimePrior: 0,
        sampleCount: 0,
      };
      cells.set(key, cell);
    }
    return cell;
  }

  // Pre-create the full grid so "no nearby feature" cells still exist with
  // zero point-derived risk (they still receive the uniform crime prior below).
  for (let i = 0; i < latCells; i++) {
    for (let j = 0; j < lonCells; j++) cellFor(i, j);
  }

  const pointEvents = events.filter((e) => e.spatialPrecision === "point");
  const cityAggregateEvents = events.filter((e) => e.spatialPrecision === "city_aggregate");

  for (const ev of pointEvents) {
    const sigma = SIGMA_M[ev.category];
    if (!sigma) continue; // protective_presence handled below via its own category name
    const cutoffM = sigma * CUTOFF_SIGMAS;
    const cutoffLatDeg = cutoffM / 111320;
    const cutoffLonDeg = cutoffM / (111320 * Math.cos(latRad));

    const evLatIdx = Math.floor((ev.lat - south) / cellLatDeg);
    const evLonIdx = Math.floor((ev.lon - west) / cellLonDeg);
    const latSpan = Math.ceil(cutoffLatDeg / cellLatDeg);
    const lonSpan = Math.ceil(cutoffLonDeg / cellLonDeg);

    for (let i = evLatIdx - latSpan; i <= evLatIdx + latSpan; i++) {
      if (i < 0 || i >= latCells) continue;
      for (let j = evLonIdx - lonSpan; j <= evLonIdx + lonSpan; j++) {
        if (j < 0 || j >= lonCells) continue;
        const cell = cellFor(i, j);
        const dM = haversineMeters(ev.lat, ev.lon, cell.lat, cell.lon);
        if (dM > cutoffM) continue;
        const kernel = Math.exp(-(dM * dM) / (2 * sigma * sigma));
        const contribution = ev.weight * ev.confidence * kernel;
        if (ev.category === "protective_presence") {
          cell.protective += contribution;
        } else {
          cell[toCamel(ev.category)] += contribution;
        }
        if (contribution > 0.01) cell.sampleCount += 1;
      }
    }
  }

  // City-wide crime prior: uniform across every cell in this city, scaled by
  // its own confidence (kept separate from sampleCount deliberately — see
  // file header).
  for (const ev of cityAggregateEvents) {
    for (const cell of cells.values()) {
      cell.crimePrior += ev.weight * ev.confidence;
    }
  }

  const cellList = [...cells.values()].map((c) => ({
    ...c,
    confidence: 1 - Math.exp(-CONFIDENCE_K * c.sampleCount),
  }));

  const rawRiskValues = cellList.map(
    (c) => Math.max(0, c.poorLighting + c.isolation + c.crimePrior - c.protective)
  );
  const referenceScale = Math.max(0.01, percentile(rawRiskValues, 95));

  const coverageCounts = { high: 0, moderate: 0, sparse: 0, insufficient: 0 };
  for (const c of cellList) {
    if (c.confidence >= 0.85) coverageCounts.high++;
    else if (c.confidence >= 0.6) coverageCounts.moderate++;
    else if (c.confidence >= 0.3) coverageCounts.sparse++;
    else coverageCounts.insufficient++;
  }

  const output = {
    manifest: {
      cityId: city.id,
      cityName: city.name,
      pipelineVersion: PIPELINE_VERSION,
      generatedAt: new Date().toISOString(),
      bbox: city.bbox,
      cellSizeMeters: CELL_METERS,
      cellSizeLatDeg: cellLatDeg,
      cellSizeLonDeg: cellLonDeg,
      latCells,
      lonCells,
      totalCells: cellList.length,
      coverageCounts,
      referenceScale,
      inputEventCount: events.length,
      upstreamCanonicalManifest: canonicalManifest,
      crimePrior: city.crimePrior,
    },
    cells: cellList,
  };

  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const outPath = path.join(OUTPUT_DIR, `${city.id}.json`);
  fs.writeFileSync(outPath, JSON.stringify(output));

  console.log(
    `[build-risk-grid] ${city.name}: ${cellList.length} cells (${latCells}x${lonCells}), ` +
      `referenceScale=${referenceScale.toFixed(3)}, coverage=${JSON.stringify(coverageCounts)}`
  );
  return output;
}

function toCamel(category) {
  return category.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

async function main() {
  const arg = process.argv[2];
  const targets = arg && arg !== "all" ? [arg] : CITY_IDS;
  let anyFailed = false;
  for (const cityId of targets) {
    try {
      buildGridForCity(cityId);
    } catch (e) {
      console.error(`[build-risk-grid] ${cityId}: FAILED — ${e.message}`);
      anyFailed = true;
    }
  }
  if (anyFailed) process.exit(1);
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, "/")}`) {
  main();
}
