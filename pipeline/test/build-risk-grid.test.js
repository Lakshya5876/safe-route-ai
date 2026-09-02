import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { haversineMeters, percentile, buildGridForCity } from "../src/build-risk-grid.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CANONICAL_DIR = path.join(__dirname, "..", "data", "canonical");
const GRID_OUT_DIR = path.join(__dirname, "..", "..", "backend", "src", "main", "resources", "risk-grids");

test("haversineMeters: known distance sanity check (roughly 1 degree latitude ~= 111.2km)", () => {
  const d = haversineMeters(0, 0, 1, 0);
  assert.ok(Math.abs(d - 111195) < 500, `expected ~111195m, got ${d}`);
});

test("haversineMeters: distance from a point to itself is zero", () => {
  assert.equal(haversineMeters(28.6, 77.2, 28.6, 77.2), 0);
});

test("haversineMeters: monotonic — a farther point is never reported as closer", () => {
  const near = haversineMeters(28.6, 77.2, 28.601, 77.2);
  const far = haversineMeters(28.6, 77.2, 28.62, 77.2);
  assert.ok(far > near);
});

test("percentile: 95th percentile of a known small array", () => {
  const values = Array.from({ length: 100 }, (_, i) => i + 1); // 1..100
  const p95 = percentile(values, 95);
  assert.ok(p95 >= 95 && p95 <= 96, `expected ~95-96, got ${p95}`);
});

test("percentile: empty array returns 0 instead of throwing", () => {
  assert.equal(percentile([], 95), 0);
});

test("percentile: single-value array returns that value", () => {
  assert.equal(percentile([42], 95), 42);
});

// --- Reproducibility: the pipeline's core promise (Phase 3 / Phase 10) ---
// These use the REAL cached canonical events already produced by an earlier
// live run against Delhi's actual OSM data (data/canonical/delhi-events.json).
// If that fixture isn't present (e.g. running tests on a machine that hasn't
// run the pipeline yet), the tests skip rather than fail — they test
// determinism of the transform, not network availability.
const delhiCanonicalExists = fs.existsSync(path.join(CANONICAL_DIR, "delhi-events.json"));

test(
  "buildGridForCity is a pure function of its input: same canonical events -> byte-identical grid (except generatedAt)",
  { skip: !delhiCanonicalExists && "requires data/canonical/delhi-events.json (run the pipeline first)" },
  () => {
    const run1 = buildGridForCity("delhi");
    const run2 = buildGridForCity("delhi");

    assert.equal(run1.cells.length, run2.cells.length);
    assert.equal(run1.manifest.referenceScale, run2.manifest.referenceScale);
    assert.equal(run1.manifest.totalCells, run2.manifest.totalCells);

    for (let i = 0; i < run1.cells.length; i++) {
      assert.deepEqual(
        { ...run1.cells[i] },
        { ...run2.cells[i] },
        `cell ${i} should be identical across runs`
      );
    }
  }
);

test(
  "grid cells never have negative combined risk fields and confidence stays in [0,1]",
  { skip: !delhiCanonicalExists && "requires data/canonical/delhi-events.json" },
  () => {
    const { cells } = buildGridForCity("delhi");
    for (const c of cells) {
      assert.ok(c.poorLighting >= 0, "poorLighting should be non-negative");
      assert.ok(c.isolation >= 0, "isolation should be non-negative");
      assert.ok(c.protective >= 0, "protective should be non-negative");
      assert.ok(c.crimePrior >= 0, "crimePrior should be non-negative");
      assert.ok(c.confidence >= 0 && c.confidence <= 1, `confidence out of range: ${c.confidence}`);
      assert.ok(c.sampleCount >= 0);
    }
  }
);

test(
  "a cell with zero local sample count has confidence 0 (no local evidence != fabricated safety)",
  { skip: !delhiCanonicalExists && "requires data/canonical/delhi-events.json" },
  () => {
    const { cells } = buildGridForCity("delhi");
    const zeroSampleCells = cells.filter((c) => c.sampleCount === 0);
    assert.ok(zeroSampleCells.length > 0, "expected at least some cells with no local OSM features nearby");
    for (const c of zeroSampleCells) {
      assert.equal(c.confidence, 0);
    }
  }
);
