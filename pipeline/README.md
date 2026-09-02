# SafeRoute Data Pipeline

Builds a versioned, per-city risk grid from real, live-fetched data — no
hand-typed "risk zones." Supports six cities: Delhi, Mumbai, Bengaluru,
Hyderabad, Pune, Chennai.

## Data provenance

**Point-level environmental signals — [OpenStreetMap](https://www.openstreetmap.org/copyright) via the [Overpass API](https://overpass-api.de/):**
free, live, no API key, ODbL-licensed (attribution required — see below). Per
city, within the bounding box in `src/cities.js`, the pipeline fetches:

- `highway=*` ways tagged `lit=no` → poor-lighting risk factor
- `highway=track|path|bridleway` ways → isolation risk factor (an approximate
  proxy — most such ways are fine, but they correlate with lower footfall and
  oversight; this is documented as a proxy, not a validated isolation measure)
- `amenity=police` / `amenity=hospital` nodes → protective factors

**City-level crime prior — NCRB "Crime in India" reports:** India does not
publish geocoded, incident-level crime data (a privacy/policy constraint, not
a gap in this project's research). The finest legitimate resolution publicly
available is aggregate city/national statistics. Delhi's prior is set from a
verified, cited figure (it recorded 74.3% of all theft cases among India's 19
largest metro cities in 2022 — NCRB, Crime in India 2022, Metropolitan Cities
Snapshot). The other five cities carry an explicitly-flagged **unverified
neutral placeholder** (`crimePrior.verified: false` in `src/cities.js`) rather
than an invented number — the full per-city NCRB statistical tables are a
>10MB PDF that could not be reliably parsed within this project's data-access
constraints. See `src/cities.js` for the exact citation per city.

**What this deliberately is NOT:** a claim that OSM lighting/road-class tags
or one city-wide crime statistic constitute ground-truth crime prediction.
They are real, honestly-sourced, non-fabricated proxies. See the risk model
section of the project report for how confidence/coverage are used to keep
that honest at query time.

## Pipeline stages

```
Overpass API (live)
      │  fetch-osm-features.js — retries + mirror fallback; never overwrites
      │  a good cache with a failed fetch
      ▼
data/raw/<city>-osm-latest.json         (versioned + timestamped)
      │  build-canonical-events.js — validates every record against the
      │  canonical SafetyFactor schema (canonical-schema.js); refuses to
      │  publish if >50% of candidates fail validation
      ▼
data/canonical/<city>-events.json       (manifest: accepted/rejected counts, reasons)
      │  build-risk-grid.js — Gaussian kernel-density spatial model,
      │  per-city 95th-percentile normalization (NOT a global sum across
      │  cities — see the report for why the old approach was wrong)
      ▼
backend/src/main/resources/risk-grids/<city>.json   (loaded by the backend at startup)
```

Run the whole thing for one city or all six:

```bash
npm run build           # all six cities
npm run build:city delhi   # just one
```

Each stage can also be run standalone (`npm run fetch`, `npm run canonical`,
`npm run grid`) — useful when only the raw OSM cache needs refreshing, since
`build-canonical-events.js` and `build-risk-grid.js` are pure functions of
their input files and always produce the same output for the same input
(verified in `test/build-risk-grid.test.js`).

## Reproducibility & validation

- `build-canonical-events.js` and `build-risk-grid.js` are deterministic —
  same input file, same output, every time (no timestamps or randomness in
  the transform itself; only the fetch stage touches the network).
- A malformed or mostly-rejected input **never overwrites a previously good
  output file** — `build-canonical-events.js` throws before writing if more
  than half of the candidates it derived from a raw fetch fail schema
  validation.
- Every output file carries a manifest: source, mirror used, fetch timestamp,
  accepted/rejected counts and reasons, pipeline version, and (for the risk
  grid) the per-city `referenceScale` normalization constant.

## Canonical data model

See `src/canonical-schema.js` for the full `SafetyFactor` schema and the
reasoning behind each field. The risk model only ever consumes this shape —
adding a new data source (e.g. a real incident feed, if one becomes publicly
available) means writing a new adapter that emits `SafetyFactor` records, not
modifying the risk model.

## Tests

```bash
npm test
```

26 tests covering schema validation edge cases, the OSM adapter's mapping
logic, and pipeline reproducibility/invariants against the real cached Delhi
data (skipped automatically if that cache isn't present).

## Coverage limitation

Each city's bounding box (`src/cities.js`) is a dense central metropolitan
core, not the full administrative metro area — Overpass times out or gets
throttled on very large areas, and a smaller well-covered box produces more
reliable, higher-confidence data than a huge sparse one. Anywhere outside a
city's box, or with too little local OSM data inside it, is reported to the
frontend as low-confidence / insufficient coverage — never silently treated
as "safe."

## Archived

`archive/process-csv-to-zones.js` and `archive/sample-delhi-crimes.csv` are
the original hand-curated 16-row Delhi-only dataset. Kept for reference, no
longer used — see the project report for why it was replaced.
