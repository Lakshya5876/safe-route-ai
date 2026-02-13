package com.saferoute.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
public class ORSRoutingService {

    /** Single route result from ORS (coordinates + duration in seconds). */
    public static class ORSRouteResult {
        public List<List<Double>> coordinates;
        public double durationSeconds;

        public ORSRouteResult(List<List<Double>> coordinates, double durationSeconds) {
            this.coordinates = coordinates;
            this.durationSeconds = durationSeconds;
        }
    }

    @Value("${ors.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Max alternatives to request from ORS (ORS may return fewer). */
    private static final int REQUEST_ALTERNATIVES = 3;

    /**
     * Return exactly what ORS provides. No padding, no via-waypoint fallback, no duplication.
     * If ORS returns 1 route → 1 route. If 2 → 2. If 3 → 3.
     */
    public List<ORSRouteResult> getMultipleRoutes(
            double originLat,
            double originLng,
            double destLat,
            double destLng
    ) {
        return requestAlternativesOrDirect(originLng, originLat, destLng, destLat);
    }

    /** Call ORS with alternative_routes; on failure or empty, get single direct route. Returns only what ORS gives. */
    private List<ORSRouteResult> requestAlternativesOrDirect(
            double originLng, double originLat, double destLng, double destLat
    ) {
        try {
            String url = "https://api.openrouteservice.org/v2/directions/driving-car/geojson";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = """
            {
              "coordinates": [
                [%f, %f],
                [%f, %f]
              ],
              "alternative_routes": {
                "target_count": %d,
                "share_factor": 0.6
              }
            }
            """.formatted(originLng, originLat, destLng, destLat, REQUEST_ALTERNATIVES);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return Collections.emptyList();

            List<Map<String, Object>> features = (List<Map<String, Object>>) responseBody.get("features");
            if (features == null || features.isEmpty()) {
                return getSingleRouteFallback(originLat, originLng, destLat, destLng);
            }

            List<ORSRouteResult> results = new ArrayList<>();
            for (Map<String, Object> feature : features) {
                ORSRouteResult r = parseFeature(feature);
                if (r != null) results.add(r);
            }
            return results;
        } catch (Exception e) {
            System.out.println("ORS alternatives ERROR: " + e.getMessage() + " — trying single route.");
            return getSingleRouteFallback(originLat, originLng, destLat, destLng);
        }
    }

    @SuppressWarnings("unchecked")
    private ORSRouteResult parseFeature(Map<String, Object> feature) {
        if (feature == null) return null;
        Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
        Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
        if (geometry == null) return null;

        Object coordsObj = geometry.get("coordinates");
        if (!(coordsObj instanceof List)) return null;
        List<?> coordList = (List<?>) coordsObj;
        if (coordList.size() < 2) return null;

        List<List<Double>> coordinates = new ArrayList<>();
        for (Object pointObj : coordList) {
            if (!(pointObj instanceof List)) return null;
            List<?> point = (List<?>) pointObj;
            if (point.size() < 2) return null;
            double lng = toDouble(point.get(0));
            double lat = toDouble(point.get(1));
            coordinates.add(List.of(lng, lat));
        }
        if (coordinates.size() < 2) return null;

        double durationSeconds = 0;
        if (properties != null) {
            Map<String, Object> summary = (Map<String, Object>) properties.get("summary");
            if (summary != null && summary.get("duration") != null) {
                Object d = summary.get("duration");
                if (d instanceof Number) durationSeconds = ((Number) d).doubleValue();
            }
        }
        return new ORSRouteResult(coordinates, durationSeconds);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) return Double.parseDouble(o.toString());
        return 0;
    }

    private List<ORSRouteResult> getSingleRouteFallback(
            double originLat, double originLng, double destLat, double destLng
    ) {
        try {
            String url =
                    "https://api.openrouteservice.org/v2/directions/driving-car/geojson";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = """
            {
              "coordinates": [
                [%f, %f],
                [%f, %f]
              ]
            }
            """.formatted(originLng, originLat, destLng, destLat);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return Collections.emptyList();
            List<Map<String, Object>> features =
                    (List<Map<String, Object>>) responseBody.get("features");
            if (features == null || features.isEmpty()) return Collections.emptyList();

            ORSRouteResult r = parseFeature(features.get(0));
            if (r != null) return new ArrayList<>(List.of(r));
            return new ArrayList<>();
        } catch (Exception e2) {
            System.out.println("ORS single route ERROR: " + e2.getMessage());
            return Collections.emptyList();
        }
    }

    /** Legacy: single route (first of alternatives). */
    public List<List<Double>> getRouteCoordinates(
            double originLat,
            double originLng,
            double destLat,
            double destLng
    ) {
        List<ORSRouteResult> routes = getMultipleRoutes(originLat, originLng, destLat, destLng);
        if (routes.isEmpty()) return Collections.emptyList();
        return routes.get(0).coordinates;
    }
}
