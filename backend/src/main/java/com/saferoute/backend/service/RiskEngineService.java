package com.saferoute.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.backend.model.RiskBreakdown;
import com.saferoute.backend.model.RiskZone;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.*;

@Service
public class RiskEngineService {

    private List<RiskZone> riskZones = new ArrayList<>();

    @PostConstruct
    public void loadRiskData() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream =
                    getClass().getResourceAsStream("/risk-data.json");

            riskZones = mapper.readValue(
                    inputStream,
                    new TypeReference<List<RiskZone>>() {}
            );

            System.out.println("Risk data loaded: "
                    + riskZones.size() + " zones");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 MAIN ROUTE RISK ENGINE
    public double calculateRouteRisk(
            List<List<Double>> routeCoordinates,
            String time
    ) {

        if (routeCoordinates == null || routeCoordinates.isEmpty()) {
            return 0;
        }

        double totalZoneRisk = 0;

        // Track zones already counted
        Set<String> intersectedZones = new HashSet<>();

        for (RiskZone zone : riskZones) {

            for (List<Double> point : routeCoordinates) {

                double lon = point.get(0);
                double lat = point.get(1);

                double distance =
                        haversine(lat, lon,
                                  zone.getLatitude(),
                                  zone.getLongitude());

                if (distance <= zone.getRadius()) {

                    intersectedZones.add(zone.getName());
                    break; // stop checking more points for this zone
                }
            }
        }

        // Add risk once per intersected zone
        for (RiskZone zone : riskZones) {
            if (intersectedZones.contains(zone.getName())) {
                totalZoneRisk += zone.getBaseRisk();
            }
        }

        // Normalize to 0–100 scale
        double maxPossibleRisk =
                riskZones.stream()
                        .mapToDouble(RiskZone::getBaseRisk)
                        .sum();

        double normalizedRisk =
                (totalZoneRisk / maxPossibleRisk) * 100;

        // 🔹 Time modifier
        if (time != null && !time.isEmpty()) {

            LocalTime userTime = LocalTime.parse(time);

            if (userTime.isAfter(LocalTime.of(23, 0))
                    || userTime.isBefore(LocalTime.of(4, 0))) {

                normalizedRisk += 15;

            } else if (userTime.isAfter(LocalTime.of(20, 0))) {

                normalizedRisk += 8;
            }
        }

        // Cap at 100
        return Math.min(normalizedRisk, 100);
    }

    // 🔹 Risk Breakdown
    public List<RiskBreakdown> getRouteBreakdown(
            List<List<Double>> routeCoordinates,
            String time
    ) {

        List<RiskBreakdown> breakdown = new ArrayList<>();

        if (routeCoordinates == null || routeCoordinates.isEmpty()) {
            return breakdown;
        }

        for (RiskZone zone : riskZones) {

            for (List<Double> point : routeCoordinates) {

                double lon = point.get(0);
                double lat = point.get(1);

                double distance =
                        haversine(lat, lon,
                                  zone.getLatitude(),
                                  zone.getLongitude());

                if (distance <= zone.getRadius()) {

                    breakdown.add(
                            new RiskBreakdown(
                                    "Route intersects: " + zone.getName(),
                                    zone.getBaseRisk()
                            )
                    );
                    break;
                }
            }
        }

        if (time != null && !time.isEmpty()) {

            LocalTime userTime = LocalTime.parse(time);

            if (userTime.isAfter(LocalTime.of(23, 0))
                    || userTime.isBefore(LocalTime.of(4, 0))) {

                breakdown.add(
                        new RiskBreakdown("Late Night Risk Modifier", 15)
                );

            } else if (userTime.isAfter(LocalTime.of(20, 0))) {

                breakdown.add(
                        new RiskBreakdown("Evening Risk Modifier", 8)
                );
            }
        }

        return breakdown;
    }

    // 🔹 Distance Calculator (Haversine Formula)
    private double haversine(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        final int EARTH_RADIUS = 6371; // km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );

        return EARTH_RADIUS * c;
    }
}
