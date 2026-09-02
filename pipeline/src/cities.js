/**
 * Canonical registry of the six cities SafeRoute supports.
 *
 * `bbox` is [south, west, north, east] in WGS84 degrees and defines the
 * fetch/coverage area used by the pipeline. This is deliberately the dense
 * central metropolitan core of each city, not the full administrative
 * metro area — Overpass (the OSM query API) times out or gets throttled on
 * very large bounding boxes, and a smaller, well-covered core produces more
 * reliable, higher-confidence data than a huge sparse one. This is a scoping
 * decision, not a hidden limitation: `coverageNote` documents it, and the
 * risk model reports "no data" (not "safe") for anything outside the box.
 *
 * `crimePrior` is a per-city, city-wide (not per-point) severity term derived
 * from NCRB's "Crime in India" publications. It is intentionally coarse:
 * NCRB does not publish geocoded incident-level crime data for India (privacy
 * policy), so this is the finest legitimate resolution available for crime
 * statistics. `verified` distinguishes a number we could directly confirm
 * against a fetched primary source from a neutral placeholder used because we
 * could not confirm city-specific figures within this project's data-access
 * constraints (the full NCRB statistical tables are a >10MB PDF that could
 * not be reliably parsed). See pipeline/README.md "Data provenance" for the
 * full citation trail. This is intentionally not extrapolated or estimated —
 * an unverified city gets the documented neutral value, not a guess.
 */

export const CITIES = {
  delhi: {
    id: "delhi",
    name: "Delhi",
    bbox: [28.56, 77.14, 28.70, 77.28],
    center: [28.6139, 77.209],
    coverageNote: "Central Delhi core (Connaught Place / Old Delhi / South Delhi belt).",
    crimePrior: {
      value: 1.0,
      verified: true,
      basis:
        "NCRB 'Crime in India 2022' Metropolitan Cities Snapshot: Delhi alone accounted for " +
        "2,05,545 of 2,76,460 (74.3%) of all theft cases registered across India's 19 largest " +
        "metro cities in 2022 — an extreme, verified outlier used to justify an elevated prior.",
      source: "NCRB, Crime in India 2022, Metropolitan Cities Snapshot (Section N)",
      sourceUrl:
        "https://www.ncrb.gov.in/uploads/nationalcrimerecordsbureau/custom/ciiyearwise2022/17016097489bCII2022Snapshots-MegaCities.pdf",
    },
  },
  mumbai: {
    id: "mumbai",
    name: "Mumbai",
    bbox: [18.9, 72.79, 19.2, 72.98],
    center: [19.076, 72.8777],
    coverageNote: "Island city + inner suburbs (Colaba to Bandra/Kurla).",
    crimePrior: {
      value: 0.5,
      verified: false,
      basis:
        "No city-specific NCRB figure could be verified within this session's data-access " +
        "constraints (full per-city statistical tables are a >10MB PDF not reliably parseable " +
        "here). Set to the documented neutral baseline rather than estimated.",
      source: "Not verified — placeholder pending real data ingestion",
      sourceUrl: null,
    },
  },
  bengaluru: {
    id: "bengaluru",
    name: "Bengaluru",
    bbox: [12.9, 77.55, 13.05, 77.68],
    center: [12.9716, 77.5946],
    coverageNote: "Central Bengaluru (MG Road to Koramangala/Indiranagar belt).",
    crimePrior: {
      value: 0.5,
      verified: false,
      basis: "Not verified within session constraints — documented neutral baseline.",
      source: "Not verified — placeholder pending real data ingestion",
      sourceUrl: null,
    },
  },
  hyderabad: {
    id: "hyderabad",
    name: "Hyderabad",
    bbox: [17.32, 78.38, 17.48, 78.52],
    center: [17.385, 78.4867],
    coverageNote: "Central Hyderabad (Charminar to Banjara Hills/Hitech City approach).",
    crimePrior: {
      value: 0.5,
      verified: false,
      basis:
        "Secondary reporting (ORF/Drishti IAS analyses of NCRB 2022) ranks Hyderabad among the " +
        "safer large metros by cognizable-crime rate, but no primary-source exact figure was " +
        "confirmed here — kept at the neutral baseline rather than lowered on unverified ranking.",
      source: "Not verified against primary NCRB tables — placeholder",
      sourceUrl: null,
    },
  },
  pune: {
    id: "pune",
    name: "Pune",
    bbox: [18.48, 73.78, 18.58, 73.9],
    center: [18.5204, 73.8567],
    coverageNote: "Central Pune (Shivajinagar to Koregaon Park/Camp belt).",
    crimePrior: {
      value: 0.5,
      verified: false,
      basis:
        "Secondary reporting (ORF/Drishti IAS analyses of NCRB 2022) ranks Pune among the " +
        "safer large metros by cognizable-crime rate, but no primary-source exact figure was " +
        "confirmed here — kept at the neutral baseline rather than lowered on unverified ranking.",
      source: "Not verified against primary NCRB tables — placeholder",
      sourceUrl: null,
    },
  },
  chennai: {
    id: "chennai",
    name: "Chennai",
    bbox: [13.0, 80.2, 13.13, 80.3],
    center: [13.0827, 80.2707],
    coverageNote: "Central Chennai (Egmore/T. Nagar to Mylapore belt).",
    crimePrior: {
      value: 0.5,
      verified: false,
      basis: "Not verified within session constraints — documented neutral baseline.",
      source: "Not verified — placeholder pending real data ingestion",
      sourceUrl: null,
    },
  },
};

export const CITY_IDS = Object.keys(CITIES);

export function getCity(id) {
  const c = CITIES[String(id || "").toLowerCase()];
  if (!c) {
    throw new Error(
      `Unknown city "${id}". Supported cities: ${CITY_IDS.join(", ")}`
    );
  }
  return c;
}
