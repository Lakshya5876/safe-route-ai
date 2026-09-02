/**
 * Canonical safety-factor schema. Every risk source in SafeRoute — OSM
 * environmental features today, a future crime-incident feed or
 * accident-blackspot dataset tomorrow — is transformed into this one shape
 * before the risk model ever sees it. The risk model only knows this schema;
 * it has no source-specific logic. Adding a new dataset means writing a new
 * adapter that emits SafetyFactor records, not touching the risk model.
 *
 * Fields, and why each exists:
 *  - lat, lon           : WGS84 point. Required — nothing is usable without it.
 *  - category           : one of CATEGORIES below. Drives which kernel width/
 *                          time-of-day rule applies (see risk-model.js).
 *  - polarity           : "risk" (raises nearby risk) or "protective" (lowers
 *                          it). Needed because police/hospital presence should
 *                          subtract, not add, and conflating the two into one
 *                          signed "weight" made the old code error-prone.
 *  - weight             : base strength in [0,1] before spatial decay. Fixed
 *                          per category (see CATEGORY_WEIGHT) rather than
 *                          hand-picked per record — see risk-model.js for the
 *                          justification of each constant.
 *  - spatialPrecision   : "point" (exact node/way location) | "city_aggregate"
 *                          (a citywide statistic with no meaningful sub-city
 *                          location). Lets the model apply a point's kernel
 *                          normally while spreading a city_aggregate factor
 *                          uniformly across the city's own cells only.
 *  - source, sourceUrl, datasetVersion, ingestedAt : provenance. Required on
 *                          every record so the pipeline manifest and the final
 *                          risk grid can always answer "where did this number
 *                          come from and when."
 *  - confidence          : [0,1], source-intrinsic reliability (an OSM node
 *                          that exists on the map is fairly certain; an
 *                          unverified city-level placeholder is not). This is
 *                          NOT the same as the grid cell's derived confidence
 *                          (which also factors in local sample density) —
 *                          this is the input-side honesty signal, that one is
 *                          the output-side honesty signal.
 */

export const CATEGORIES = Object.freeze([
  "poor_lighting",
  "isolation",
  "protective_presence",
  "crime_prior",
]);

export const REQUIRED_FIELDS = Object.freeze([
  "lat",
  "lon",
  "category",
  "polarity",
  "weight",
  "spatialPrecision",
  "source",
  "datasetVersion",
  "ingestedAt",
  "confidence",
]);

/**
 * Validate one raw candidate record. Returns { ok: true, event } or
 * { ok: false, reason }. Never throws — callers use this to build an
 * accept/reject report rather than crashing the whole pipeline on one bad row.
 */
export function validateSafetyFactor(rec) {
  if (rec == null || typeof rec !== "object") {
    return { ok: false, reason: "not an object" };
  }
  for (const f of REQUIRED_FIELDS) {
    if (rec[f] === undefined || rec[f] === null || rec[f] === "") {
      return { ok: false, reason: `missing required field '${f}'` };
    }
  }
  const lat = Number(rec.lat);
  const lon = Number(rec.lon);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
    return { ok: false, reason: `invalid lat '${rec.lat}'` };
  }
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) {
    return { ok: false, reason: `invalid lon '${rec.lon}'` };
  }
  if (!CATEGORIES.includes(rec.category)) {
    return { ok: false, reason: `unknown category '${rec.category}'` };
  }
  if (!["risk", "protective"].includes(rec.polarity)) {
    return { ok: false, reason: `invalid polarity '${rec.polarity}'` };
  }
  const weight = Number(rec.weight);
  if (!Number.isFinite(weight) || weight < 0 || weight > 1) {
    return { ok: false, reason: `weight '${rec.weight}' out of [0,1]` };
  }
  if (!["point", "city_aggregate"].includes(rec.spatialPrecision)) {
    return { ok: false, reason: `invalid spatialPrecision '${rec.spatialPrecision}'` };
  }
  const confidence = Number(rec.confidence);
  if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1) {
    return { ok: false, reason: `confidence '${rec.confidence}' out of [0,1]` };
  }
  if (Number.isNaN(Date.parse(rec.ingestedAt))) {
    return { ok: false, reason: `invalid ingestedAt '${rec.ingestedAt}'` };
  }

  return {
    ok: true,
    event: {
      lat,
      lon,
      category: rec.category,
      polarity: rec.polarity,
      weight,
      spatialPrecision: rec.spatialPrecision,
      source: String(rec.source),
      sourceUrl: rec.sourceUrl ?? null,
      datasetVersion: String(rec.datasetVersion),
      ingestedAt: rec.ingestedAt,
      confidence,
    },
  };
}
