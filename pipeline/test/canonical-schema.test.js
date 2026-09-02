import { test } from "node:test";
import assert from "node:assert/strict";
import { validateSafetyFactor, CATEGORIES } from "../src/canonical-schema.js";

function validRecord(overrides = {}) {
  return {
    lat: 28.6,
    lon: 77.2,
    category: "poor_lighting",
    polarity: "risk",
    weight: 0.6,
    spatialPrecision: "point",
    source: "OpenStreetMap via Overpass API",
    sourceUrl: "https://www.openstreetmap.org/copyright",
    datasetVersion: "osm-2026-01-01T00:00:00Z",
    ingestedAt: "2026-01-01T00:00:00Z",
    confidence: 0.85,
    ...overrides,
  };
}

test("accepts a well-formed record", () => {
  const result = validateSafetyFactor(validRecord());
  assert.equal(result.ok, true);
  assert.equal(result.event.category, "poor_lighting");
});

test("rejects missing required fields", () => {
  for (const field of ["lat", "lon", "category", "polarity", "weight", "spatialPrecision", "source", "datasetVersion", "ingestedAt", "confidence"]) {
    const rec = validRecord({ [field]: undefined });
    const result = validateSafetyFactor(rec);
    assert.equal(result.ok, false, `expected rejection for missing '${field}'`);
    assert.match(result.reason, new RegExp(field));
  }
});

test("rejects out-of-range latitude and longitude", () => {
  assert.equal(validateSafetyFactor(validRecord({ lat: 91 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ lat: -91 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ lon: 181 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ lon: "not-a-number" })).ok, false);
});

test("rejects unknown category", () => {
  const result = validateSafetyFactor(validRecord({ category: "made_up_category" }));
  assert.equal(result.ok, false);
  assert.match(result.reason, /unknown category/);
});

test("every declared category is accepted", () => {
  for (const cat of CATEGORIES) {
    const result = validateSafetyFactor(validRecord({ category: cat }));
    assert.equal(result.ok, true, `category '${cat}' should be valid`);
  }
});

test("rejects invalid polarity", () => {
  assert.equal(validateSafetyFactor(validRecord({ polarity: "neutral" })).ok, false);
});

test("rejects weight and confidence outside [0,1]", () => {
  assert.equal(validateSafetyFactor(validRecord({ weight: 1.5 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ weight: -0.1 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ confidence: 2 })).ok, false);
  assert.equal(validateSafetyFactor(validRecord({ confidence: -1 })).ok, false);
});

test("rejects an unparsable ingestedAt timestamp", () => {
  const result = validateSafetyFactor(validRecord({ ingestedAt: "not-a-date" }));
  assert.equal(result.ok, false);
  assert.match(result.reason, /ingestedAt/);
});

test("rejects invalid spatialPrecision", () => {
  assert.equal(validateSafetyFactor(validRecord({ spatialPrecision: "street" })).ok, false);
});

test("does not throw on garbage input (null, array, primitive)", () => {
  assert.equal(validateSafetyFactor(null).ok, false);
  assert.equal(validateSafetyFactor(undefined).ok, false);
  assert.equal(validateSafetyFactor("a string").ok, false);
  assert.equal(validateSafetyFactor(42).ok, false);
  assert.equal(validateSafetyFactor([]).ok, false);
});
