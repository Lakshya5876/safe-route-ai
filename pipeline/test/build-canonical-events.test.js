import { test } from "node:test";
import assert from "node:assert/strict";
import { osmToCandidates, cityPriorToCandidate } from "../src/build-canonical-events.js";
import { validateSafetyFactor } from "../src/canonical-schema.js";
import { getCity } from "../src/cities.js";

function fakeOsmRecord(elements) {
  return {
    fetchedAt: "2026-01-01T00:00:00.000Z",
    osmBaseTimestamp: "2025-12-31T00:00:00Z",
    elements,
  };
}

test("maps an unlit way to a poor_lighting risk candidate", () => {
  const rec = fakeOsmRecord([
    { type: "way", id: 1, tags: { highway: "residential", lit: "no" }, center: { lat: 28.6, lon: 77.2 } },
  ]);
  const candidates = osmToCandidates(rec);
  assert.equal(candidates.length, 1);
  assert.equal(candidates[0].category, "poor_lighting");
  assert.equal(candidates[0].polarity, "risk");
  assert.equal(validateSafetyFactor(candidates[0]).ok, true);
});

test("maps track/path/bridleway ways to isolation risk candidates", () => {
  for (const highway of ["track", "path", "bridleway"]) {
    const rec = fakeOsmRecord([
      { type: "way", id: 2, tags: { highway }, center: { lat: 28.6, lon: 77.2 } },
    ]);
    const candidates = osmToCandidates(rec);
    assert.equal(candidates.length, 1, `expected a candidate for highway=${highway}`);
    assert.equal(candidates[0].category, "isolation");
  }
});

test("maps police and hospital nodes to protective candidates with different weights", () => {
  const rec = fakeOsmRecord([
    { type: "node", id: 3, lat: 28.6, lon: 77.2, tags: { amenity: "police" } },
    { type: "node", id: 4, lat: 28.61, lon: 77.21, tags: { amenity: "hospital" } },
  ]);
  const candidates = osmToCandidates(rec);
  assert.equal(candidates.length, 2);
  assert.ok(candidates.every((c) => c.polarity === "protective"));
  const police = candidates.find((c) => c.weight === 0.8);
  const hospital = candidates.find((c) => c.weight === 0.5);
  assert.ok(police, "expected a police candidate with weight 0.8");
  assert.ok(hospital, "expected a hospital candidate with weight 0.5");
});

test("ignores elements outside the adapter's scope without rejecting anything", () => {
  const rec = fakeOsmRecord([
    { type: "node", id: 5, lat: 28.6, lon: 77.2, tags: { shop: "bakery" } },
    { type: "way", id: 6, tags: { highway: "primary" }, center: { lat: 28.6, lon: 77.2 } }, // no lit=no, not track/path
  ]);
  const candidates = osmToCandidates(rec);
  assert.equal(candidates.length, 0);
});

test("skips elements with no usable coordinate instead of crashing", () => {
  const rec = fakeOsmRecord([
    { type: "way", id: 7, tags: { highway: "residential", lit: "no" } }, // no center, no lat/lon
  ]);
  const candidates = osmToCandidates(rec);
  assert.equal(candidates.length, 0);
});

test("every candidate produced by osmToCandidates passes schema validation", () => {
  const rec = fakeOsmRecord([
    { type: "way", id: 1, tags: { highway: "residential", lit: "no" }, center: { lat: 28.6, lon: 77.2 } },
    { type: "way", id: 2, tags: { highway: "track" }, center: { lat: 28.61, lon: 77.21 } },
    { type: "node", id: 3, lat: 28.62, lon: 77.22, tags: { amenity: "police" } },
    { type: "node", id: 4, lat: 28.63, lon: 77.23, tags: { amenity: "hospital" } },
  ]);
  const candidates = osmToCandidates(rec);
  assert.equal(candidates.length, 4);
  for (const c of candidates) {
    const result = validateSafetyFactor(c);
    assert.equal(result.ok, true, `candidate should validate: ${result.reason ?? ""}`);
  }
});

test("cityPriorToCandidate reflects the verified/unverified distinction honestly", () => {
  const delhi = cityPriorToCandidate(getCity("delhi"));
  const mumbai = cityPriorToCandidate(getCity("mumbai"));

  assert.equal(delhi.spatialPrecision, "city_aggregate");
  assert.equal(delhi.category, "crime_prior");
  assert.equal(validateSafetyFactor(delhi).ok, true);
  assert.equal(validateSafetyFactor(mumbai).ok, true);

  // Delhi's prior is verified against a primary source -> higher input confidence
  // than Mumbai's documented, unverified placeholder. This is a direct check that
  // the pipeline does not quietly launder an unverified number into a confident one.
  assert.ok(delhi.confidence > mumbai.confidence);
  assert.equal(mumbai.confidence, 0.25);
  assert.equal(delhi.confidence, 0.6);
});
