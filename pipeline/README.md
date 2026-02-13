# SafeRoute Delhi – Risk Data Pipeline

## Overview

Converts raw CSV (NCRB, Delhi Police, civic data) into `delhi-risk-zones.json` for the backend RiskEngine.

## Folder structure

```
pipeline/
  package.json
  process-csv-to-zones.js   # Preprocessing script
  sample-delhi-crimes.csv   # Example input
  README.md
backend/src/main/resources/
  risk-data.json            # Fallback (existing)
  delhi-risk-zones.json     # Output (generated)
```

## CSV format

Input CSV should have (column names case-insensitive):

- `lat` or `latitude` – latitude (WGS84)
- `lng` or `longitude` or `lon` – longitude
- `category` or `type` – one of: assault, theft, accident_prone, poor_lighting, unsafe_pedestrian, other
- `severity` or `base_risk` or `baseRisk` – number (optional; category default used if missing)
- `name` or `area` – zone label (optional)
- `radius_km` or `radius` – radius in km (optional; default 0.8)

Rows outside Delhi bounds (lat 28.4–28.9, lng 76.8–77.4) are dropped.

## Run instructions

### 1. Process sample CSV (no extra files)

```bash
cd pipeline
npm run process:sample
```

Or with a custom file:

```bash
node process-csv-to-zones.js path/to/your.csv
```

Output is written to `backend/src/main/resources/delhi-risk-zones.json`.

### 2. Run backend

```bash
cd backend
mvn spring-boot:run
```

Backend loads `delhi-risk-zones.json` if present, otherwise `risk-data.json`.

### 3. Run frontend

```bash
cd frontend
npm install
npm run dev
```

## End-to-end flow

```
CSV (raw) → process-csv-to-zones.js → delhi-risk-zones.json
                                              ↓
                    Spring Boot RiskEngine (loads JSON)
                                              ↓
                    Segment risk + time multipliers by category
                                              ↓
                    POST /api/route → routes[] with riskScore, description
                                              ↓
                    Mapbox: primary green, segments HIGH/MODERATE/LOW/SAFE
```

## Adding real data

1. Download CSV from NCRB, Delhi Police, or civic open data (Delhi).
2. Map columns to: lat, lng, category, severity (and optionally name, radius_km).
3. Run: `node process-csv-to-zones.js your-file.csv`
4. Restart the backend.
