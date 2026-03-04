# SafeRoute AI

**Context-Aware Navigation Focused on Urban Safety**

## Overview

SafeRoute AI is a backend system that evaluates the **safety of navigation routes** using contextual risk signals.

Most navigation systems optimize only for **speed and traffic efficiency**.
SafeRoute introduces a **risk scoring engine** that evaluates routes based on safety indicators such as:

* crime cluster proximity
* isolated road segments
* time-of-day vulnerability
* user safety preferences

Instead of predicting crime, SafeRoute aggregates contextual risk factors and computes a **transparent safety score** for each route.

The system returns:

* Total Risk Score
* Risk Level (Low / Moderate / High)
* Risk Factor Breakdown

---

## Tech Stack

### Backend

* Java
* Spring Boot
* REST API architecture

### Frontend

* React (UI prototype)

### Data Layer

* JSON-based geo-risk dataset

### Planned Integrations

* Open Route Service API
* Polyline decoding for route segmentation
* Spatial datasets for crime mapping

---

## Key Features

* Safety-aware route evaluation
* Deterministic risk scoring engine
* Time-of-day risk multiplier
* Geo-zone risk detection
* Risk classification system
* Modular architecture for future data-driven models

---

## System Architecture

```
User Request
     ↓
React Frontend
     ↓
Spring Boot REST API
     ↓
RiskEngineService
     ↓
Geo-Risk Dataset
     ↓
Safety Score Response
```

Architecture follows a **layered backend structure**:

```
Controller → Service → Model → Data
```

---

## Core Risk Engine

When a route safety request is made:

1. Route is retrieved via the Directions API *(planned)*
2. Route polyline is decoded into coordinate points
3. Each coordinate is evaluated against geo-risk zones
4. Time-of-day multiplier adjusts risk score
5. Final route safety score is computed
6. Risk classification and factor breakdown are returned

The risk engine is **deterministic and modular**, allowing future upgrades to machine learning based weighting.

---

## Example API Endpoint

### POST

```
/api/route/safest
```

### Request

```json
{
  "origin": "India Gate Delhi",
  "destination": "Karol Bagh Delhi",
  "time": "23:30"
}
```

### Example Response

```json
{
  "riskScore": 42,
  "riskLevel": "Moderate",
  "factors": {
    "crimeCluster": 20,
    "timeOfDay": 15,
    "isolation": 7
  }
}
```

---

## Prototype Data Strategy

For the prototype version:

* Risk zones are manually curated from publicly available crime cluster reports
* Each zone contains:

```
latitude
longitude
radius
base risk score
```

This demonstrates the framework for **route safety scoring**.

Future versions will integrate:

* NCRB crime GIS datasets
* OpenStreetMap metadata
* Spatial databases (PostGIS)
* Crowd-sourced safety signals

---

## Project Structure

```
src
 ├─ controller
 │   └─ RouteController
 ├─ service
 │   └─ RiskEngineService
 ├─ model
 ├─ data
 │   └─ riskZones.json
 └─ config
```

---

## Running the Project

### 1. Clone repository

```
git clone https://github.com/your-username/SafeRoute-AI.git
```

### 2. Navigate to project

```
cd SafeRoute-AI
```

### 3. Run Spring Boot server

```
mvn spring-boot:run
```

Server starts on:

```
http://localhost:8080
```

---

## Future Improvements

* Integration with Google Directions API
* Polyline decoding for route segment scoring
* PostGIS spatial database support
* Machine learning risk weighting
* Real-time crowd-sourced safety signals

---

Contributors

Lakshya

Vinod Prajapati
