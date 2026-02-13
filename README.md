# SafeRoute AI

Context-Aware Navigation Focused on Urban Safety

---

## Problem

Modern navigation systems optimize for speed and traffic efficiency.  
They do not account for contextual safety risks such as:

- Crime density clusters
- Late-night vulnerability
- Isolated road segments
- User safety preference

Urban mobility tools answer: “How fast can I get there?”  
SafeRoute asks: “How safely can I get there?”

---

## Solution

SafeRoute AI is a safety-aware routing system that evaluates routes using a structured risk scoring engine.

Instead of predicting crime, the system aggregates contextual risk factors and computes a transparent safety score for each route.

The output includes:

- Total Risk Score
- Risk Level (Low / Moderate / High)
- Factor-wise Risk Breakdown

---

## Architecture Overview

Frontend:
- React-based user interface
- Route input and safety controls
- Map visualization layer (in progress)

Backend:
- Spring Boot REST API
- Layered architecture (Controller → Service → Model)
- Risk scoring engine (RiskEngineService)

Data Layer:
- Curated geo-risk dataset (JSON prototype)
- Designed for future GIS-based ingestion

External Integration:
- Google Directions API (planned)
- Polyline decoding for route-segment scoring (planned)

---

## Core Risk Engine

When a request is made:

1. Route is retrieved via Directions API
2. Route is decoded into coordinate points
3. Each coordinate is evaluated against geo-risk zones
4. Time-of-day multiplier is applied
5. Final safety score is computed
6. Risk classification and breakdown are returned

The engine is deterministic and modular, allowing future transition to data-driven weighting.

---

## Prototype Data Strategy

For the hackathon prototype:

- Risk zones are curated using publicly available crime cluster reports
- Each zone is defined by latitude, longitude, radius, and base risk score
- Relative scoring demonstrates framework functionality

Production version would integrate:

- Public crime GIS datasets (NCRB, state dashboards)
- OpenStreetMap metadata
- Spatial database (e.g., PostGIS)
- Crowd-sourced risk signals

---

## API Endpoint

POST  
`/api/route/safest`

Request:
```json
{
  "origin": "India Gate Delhi",
  "destination": "Karol Bagh Delhi",
  "time": "23:30"
}
  "time": "23:30"
}
