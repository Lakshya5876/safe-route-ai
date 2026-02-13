package com.saferoute.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
public class ORSRoutingService {

    @Value("${ors.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<List<Double>> getRouteCoordinates(
            double originLat,
            double originLng,
            double destLat,
            double destLng
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

            HttpEntity<String> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null) return Collections.emptyList();

            List<Map<String, Object>> features =
                    (List<Map<String, Object>>) responseBody.get("features");

            if (features == null || features.isEmpty())
                return Collections.emptyList();

            Map<String, Object> geometry =
                    (Map<String, Object>) features.get(0).get("geometry");

            if (geometry == null)
                return Collections.emptyList();

            List<List<Double>> coordinates =
                    (List<List<Double>>) geometry.get("coordinates");

            if (coordinates == null)
                return Collections.emptyList();

            return coordinates;

        } catch (Exception e) {
            System.out.println("ORS ERROR: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
