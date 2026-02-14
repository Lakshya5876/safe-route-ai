package com.saferoute.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

/**
 * Routing only. Calls OpenRouteService with the correct profile per travel mode.
 * Returns exactly what ORS returns (N routes). No padding, no duplication.
 */
@Service
public class ORSRoutingService {

    public static class ORSRouteResult {
        public List<List<Double>> coordinates;
        public double durationSeconds;

        public ORSRouteResult(List<List<Double>> coordinates, double durationSeconds) {
            this.coordinates = coordinates;
            this.durationSeconds = durationSeconds;
        }
    }

    private static final int REQUEST_ALTERNATIVES_DEFAULT = 3;
    private static final int REQUEST_ALTERNATIVES_DRIVING = 2;

    /** ORS profile per travel mode. */
    private static String profileForMode(String mode) {
        if (mode == null) return "driving-car";
        return switch (mode.toUpperCase()) {
            case "WALKING" -> "foot-walking";
            case "BIKE" -> "cycling-regular";
            default -> "driving-car";
        };
    }

    @Value("${ors.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Return exactly what ORS provides for the given mode.
     * DRIVING → driving-car, WALKING → foot-walking, BIKE → cycling-regular.
     */
    public List<ORSRouteResult> getMultipleRoutes(
            double originLat,
            double originLng,
            double destLat,
            double destLng,
            String mode
    ) {
        String profile = profileForMode(mode);
        int alternatives = (mode != null && "DRIVING".equalsIgnoreCase(mode))
                ? REQUEST_ALTERNATIVES_DRIVING
                : REQUEST_ALTERNATIVES_DEFAULT;
        return requestDirections(originLng, originLat, destLng, destLat, profile, alternatives);
    }

    private List<ORSRouteResult> requestDirections(
            double originLng, double originLat, double destLng, double destLat,
            String profile,
            int targetCount
    ) {
        try {
            String url = "https://api.openrouteservice.org/v2/directions/" + profile + "/geojson";
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
            """.formatted(originLng, originLat, destLng, destLat, targetCount);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) return Collections.emptyList();

            List<Map<String, Object>> features = (List<Map<String, Object>>) responseBody.get("features");
            if (features == null || features.isEmpty()) {
                return requestSingleRoute(originLng, originLat, destLng, destLat, profile);
            }

            List<ORSRouteResult> results = new ArrayList<>();
            for (Map<String, Object> feature : features) {
                ORSRouteResult r = parseFeature(feature);
                if (r != null) results.add(r);
            }
            return results;
        } catch (Exception e) {
            System.out.println("ORS ERROR (" + profile + "): " + e.getMessage());
            try {
                return requestSingleRoute(originLng, originLat, destLng, destLat, profile);
            } catch (Exception e2) {
                System.out.println("ORS single-route fallback ERROR: " + e2.getMessage());
                return Collections.emptyList();
            }
        }
    }

    private List<ORSRouteResult> requestSingleRoute(
            double originLng, double originLat, double destLng, double destLat,
            String profile
    ) {
        String url = "https://api.openrouteservice.org/v2/directions/" + profile + "/geojson";
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
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) return Collections.emptyList();
        List<Map<String, Object>> features = (List<Map<String, Object>>) responseBody.get("features");
        if (features == null || features.isEmpty()) return Collections.emptyList();
        ORSRouteResult r = parseFeature(features.get(0));
        if (r != null) return new ArrayList<>(List.of(r));
        return Collections.emptyList();
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
}
