# SafeRoute AI

**Risk-aware route comparison for six Indian cities — Delhi, Mumbai, Bengaluru, Hyderabad, Pune, Chennai.**

SafeRoute generates route candidates for driving, walking, and cycling, and
scores each one for safety using a real, versioned, per-city risk grid built
from live geospatial data — not hand-typed "risk zones." It runs entirely
locally: no cloud services, no paid APIs beyond the free tiers of
OpenRouteService (routing) and Mapbox (maps/geocoding), both of which you
configure with your own free API keys.

This README describes what the system **actually does**, verified against the
running code — see `pipeline/README.md` for the data pipeline and the
project engineering report for the full risk-model derivation and test
results.

## How it works

```
Origin + destination + mode + city
        │
        ▼
OpenRouteService (routing candidates)
        │
        ▼
RiskModelService: each candidate scored against that city's precomputed
risk grid (spatial kernel density over real OSM lighting/isolation/
protective-amenity data + a documented, honestly-sourced city crime prior)
        │
        ├─ if the best candidate crosses a confirmed high-risk point,
        │  one more ORS request actively routes AROUND that area
        ▼
Deduplication (Hausdorff-style path comparison) → Pareto-optimal marking
(risk vs. duration) → a bounded-tradeoff primary recommendation
        │
        ▼
JSON response: routes[] with riskScore, confidence, data-coverage tier,
Pareto flag, and a segment-by-segment color-coded breakdown
        │
        ▼
React + Mapbox frontend
```

## Data

- **OpenStreetMap** (via the Overpass API, live, free, no key): real, per-city
  lighting/isolation/protective-amenity features. © OpenStreetMap
  contributors, ODbL license.
- **NCRB "Crime in India"**: a city-level crime severity prior. Delhi's is a
  verified, cited figure; the other five cities carry an explicitly-flagged
  unverified neutral placeholder rather than an invented number (India does
  not publish geocoded incident-level crime data). See `pipeline/README.md`
  and `pipeline/src/cities.js` for full citations.

## Risk model, in one paragraph

Each city is tiled into ~150m cells. Every risk factor contributes to nearby
cells through a distance-decay kernel (not a hard-edged circle), kept
separate by category so time-of-day can be recombined at request time.
Normalization divides by that city's own 95th-percentile cell risk — not a
sum across every city ever loaded, which was a bug in an earlier version of
this project that made adding a new city silently change every existing
city's scores. Routes are scored by resampling at a fixed arc-length step
(not by vertex count, which biased scores toward geometrically dense
stretches) and averaging risk by distance traveled — the same numbers drive
both the map's segment coloring and the route ranking, which previously used
two different, inconsistent formulas. Confidence is derived from real local
OSM feature density, not fabricated; an area with no data is reported as
low-confidence, never as "safe by default."

## Running locally

Requires: Java 21+, Maven, Node 18+, a free [OpenRouteService API key](https://openrouteservice.org/dev/#/signup), and a free [Mapbox token](https://account.mapbox.com/).

```bash
# 1. Build the risk grids (only needed once, or when you want fresher OSM data)
cd pipeline
npm install
npm run build

# 2. Configure and start the backend
cd ../backend
cp .env.example .env   # then edit .env with your real ORS_API_KEY
export ORS_API_KEY=your-real-key-here   # or use your shell/IDE's env support
mvn spring-boot:run

# 3. Configure and start the frontend (in a second terminal)
cd ../frontend
npm install
echo "VITE_MAPBOX_TOKEN=your-real-mapbox-token" > .env
npm run dev
```

Then open http://localhost:5173, pick one of the six supported cities, enter
an origin and destination within it, and choose driving/walking/cycling.

## Tests

```bash
cd pipeline && npm test      # 26 tests: schema validation, OSM adapter, reproducibility
cd backend && mvn test       # 33 tests: risk math, dedup, Pareto selection, mocked-ORS integration, performance
```

## What this is not

Not a crime-prediction system, and not a claim of prediction accuracy — there
is no incident-level ground truth publicly available in India to validate
against. It is an honestly-sourced, explainable risk **exposure** estimate
with explicit confidence and data-coverage reporting. See the project
engineering report for a full accounting of what's real, what's a documented
proxy, and what remains a known limitation.

## Project structure

```
backend/    Spring Boot API — routing orchestration, risk model, tests
frontend/   React + Mapbox UI
pipeline/   Node.js data pipeline — OSM fetch → canonical schema → risk grid
```

## Contributors

Lakshya, Vinod Prajapati
