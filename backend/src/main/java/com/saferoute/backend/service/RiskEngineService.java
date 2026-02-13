package com.saferoute.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.backend.model.RiskBreakdown;
import com.saferoute.backend.model.RiskZone;
import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.model.RouteSegmentDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.*;

@Service
public class RiskEngineService {

    private List<RiskZone> riskZones = new ArrayList<>();
    private double maxPossibleRisk;

    @PostConstruct
    public void loadRiskData() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = getClass().getResourceAsStream("/delhi-risk-zones.json");
            if (inputStream == null) {
                inputStream = getClass().getResourceAsStream("/risk-data.json");
            }
            if (inputStream == null) {
                System.err.println("No risk-data.json or delhi-risk-zones.json found");
                return;
            }

            riskZones = mapper.readValue(
                    inputStream,
                    new TypeReference<List<RiskZone>>() {}
            );

            maxPossibleRisk =
                    riskZones.stream()
                            .mapToDouble(RiskZone::getBaseRisk)
                            .sum();

            System.out.println("Risk data loaded: " + riskZones.size() + " zones");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Route risk using segment midpoints; path-length variation so different routes get different scores. */
    public double calculateRouteRisk(
            List<List<Double>> routeCoordinates,
            String time
    ) {
        RouteRiskSummary s = calculateRouteRiskWithSummary(routeCoordinates, time);
        return s != null ? s.getRiskScore() : 0;
    }

    /** Full risk summary for one route: score, zones hit, length, time flags. Used for per-route description. */
    public RouteRiskSummary calculateRouteRiskWithSummary(
            List<List<Double>> routeCoordinates,
            String time
    ) {
        if (routeCoordinates == null || routeCoordinates.size() < 2) {
            RouteRiskSummary s = new RouteRiskSummary();
            s.setRiskScore(0);
            s.setRiskLevel("LOW");
            s.setZoneNames(List.of());
            s.setTotalLengthKm(0);
            s.setNightTravel(false);
            s.setEveningTravel(false);
            return s;
        }

        // Step B: Multi-point sampling per segment so spatially distinct routes get different zone hits.
        // Sample at t=0.25, 0.5, 0.75 along each segment (not just midpoint) so small geometry differences matter.
        Set<String> intersectedZones = new HashSet<>();
        double[] segmentSampleT = { 0.25, 0.5, 0.75 };

        for (int i = 0; i < routeCoordinates.size() - 1; i++) {
            List<Double> a = routeCoordinates.get(i);
            List<Double> b = routeCoordinates.get(i + 1);
            if (a == null || b == null || a.size() < 2 || b.size() < 2) continue;
            double aLng = toDouble(a.get(0)), aLat = toDouble(a.get(1));
            double bLng = toDouble(b.get(0)), bLat = toDouble(b.get(1));

            for (double t : segmentSampleT) {
                double lng = aLng + t * (bLng - aLng);
                double lat = aLat + t * (bLat - aLat);
                for (RiskZone zone : riskZones) {
                    double distance = haversine(lat, lng, zone.getLatitude(), zone.getLongitude());
                    if (distance <= zone.getRadius()) {
                        intersectedZones.add(zone.getName());
                        break;
                    }
                }
            }
        }

        boolean nightTravel = false;
        boolean eveningTravel = false;
        if (time != null && !time.isEmpty()) {
            try {
                LocalTime userTime = LocalTime.parse(time);
                nightTravel = userTime.isAfter(LocalTime.of(23, 0)) || userTime.isBefore(LocalTime.of(4, 0));
                eveningTravel = !nightTravel && userTime.isAfter(LocalTime.of(20, 0));
            } catch (Exception ignored) {}
        }

        double totalZoneRisk = 0;
        for (RiskZone zone : riskZones) {
            if (!intersectedZones.contains(zone.getName())) continue;
            double r = zone.getBaseRisk();
            String cat = zone.getCategory() != null ? zone.getCategory().toLowerCase() : "";
            if (nightTravel && (cat.contains("assault") || cat.contains("poor_lighting") || cat.contains("lighting"))) {
                r *= 1.4;
            } else if (eveningTravel && (cat.contains("accident"))) {
                r *= 1.25;
            }
            totalZoneRisk += r;
        }

        double totalLengthKm = computeRouteLengthKm(routeCoordinates);
        int segmentCount = Math.max(0, routeCoordinates.size() - 1);

        // Zone component: 0–100 scale from zones hit (no compression; raw ratio)
        double zoneComponent = maxPossibleRisk > 0
                ? (totalZoneRisk / maxPossibleRisk) * 100
                : 0;

        if (intersectedZones.isEmpty()) {
            zoneComponent += 18; // remote baseline
        }

        // Path-length exposure: different length → different score (no flattening)
        double lengthFactor = Math.min(15, totalLengthKm * 0.4);
        // Segment-count exposure: more segments = more sampling = can capture more zone exposure
        double segmentFactor = Math.min(8, segmentCount * 0.08);

        double timeModifier = 0;
        if (nightTravel) {
            timeModifier = 28;
        } else if (eveningTravel) {
            timeModifier = 14;
        }

        double normalizedRisk = zoneComponent + lengthFactor + segmentFactor + timeModifier;
        normalizedRisk = Math.min(normalizedRisk, 100);

        // Step C: Diagnostic – route risk formula (no compression; values vary by geometry/length)
        System.out.printf("[RiskPipeline] route: coords=%d lengthKm=%.2f zonesHit=%d rawZoneRisk=%.1f zoneComp=%.1f lenFactor=%.1f segFactor=%.1f timeMod=%.0f -> riskScore=%.1f%n",
                routeCoordinates.size(), totalLengthKm, intersectedZones.size(), totalZoneRisk, zoneComponent, lengthFactor, segmentFactor, timeModifier, normalizedRisk);

        if ("true".equalsIgnoreCase(System.getProperty("saferoute.debug.risk"))) {
            double maxSeg = 0, sumSeg = 0; int segN = 0;
            for (int i = 0; i < routeCoordinates.size() - 1 && i < 20; i++) {
                List<Double> a = routeCoordinates.get(i), b = routeCoordinates.get(i + 1);
                if (a == null || b == null || a.size() < 2 || b.size() < 2) continue;
                double midLat = (toDouble(a.get(1)) + toDouble(b.get(1))) / 2;
                double midLng = (toDouble(a.get(0)) + toDouble(b.get(0))) / 2;
                double seg = segmentRiskScore(midLat, midLng, time);
                maxSeg = Math.max(maxSeg, seg); sumSeg += seg; segN++;
            }
            if (segN > 0) System.out.printf("[RiskPipeline] segmentRisk sample: max=%.1f avg=%.1f (first %d segments)%n", maxSeg, sumSeg / segN, segN);
        }

        List<String> categories = new ArrayList<>();
        for (RiskZone zone : riskZones) {
            if (intersectedZones.contains(zone.getName()) && zone.getCategory() != null && !zone.getCategory().isEmpty()) {
                if (!categories.contains(zone.getCategory())) categories.add(zone.getCategory());
            }
        }

        RouteRiskSummary s = new RouteRiskSummary();
        s.setRiskScore(normalizedRisk);
        s.setRiskLevel(routeRiskLevel(normalizedRisk));
        s.setZoneNames(new ArrayList<>(intersectedZones));
        s.setZoneCategories(categories);
        s.setTotalLengthKm(totalLengthKm);
        s.setNightTravel(nightTravel);
        s.setEveningTravel(eveningTravel);
        return s;
    }

    private double computeRouteLengthKm(List<List<Double>> routeCoordinates) {
        double total = 0;
        for (int i = 0; i < routeCoordinates.size() - 1; i++) {
            List<Double> a = routeCoordinates.get(i);
            List<Double> b = routeCoordinates.get(i + 1);
            if (a == null || b == null || a.size() < 2 || b.size() < 2) continue;
            double lat1 = toDouble(a.get(1)), lon1 = toDouble(a.get(0));
            double lat2 = toDouble(b.get(1)), lon2 = toDouble(b.get(0));
            total += haversine(lat1, lon1, lat2, lon2);
        }
        return total;
    }

    /** Customized description per route from risk summary and duration. */
    public String buildRouteDescription(RouteRiskSummary summary, double durationMin) {
        if (summary == null) return "Route overview.";
        StringBuilder sb = new StringBuilder();
        int zones = summary.getZoneNames() != null ? summary.getZoneNames().size() : 0;

        switch (summary.getRiskLevel()) {
            case "LOW" -> sb.append("Lowest risk option.");
            case "MODERATE" -> sb.append("Moderate risk.");
            case "HIGH" -> sb.append("Higher risk; consider alternatives.");
            default -> sb.append("Route overview.");
        }

        if (zones > 0) {
            sb.append(" Passes ").append(zones).append(" known risk zone(s)");
            if (summary.getZoneCategories() != null && !summary.getZoneCategories().isEmpty()) {
                sb.append(" (").append(String.join(", ", summary.getZoneCategories())).append(")");
            } else if (summary.getZoneNames() != null && !summary.getZoneNames().isEmpty()) {
                sb.append(" (e.g. ").append(summary.getZoneNames().get(0)).append(")");
            }
            sb.append(".");
        } else {
            sb.append(" No known risk zones on this path; remote-stretch baseline applied.");
        }

        if (summary.isNightTravel()) {
            sb.append(" Night travel: assault & lighting risk elevated.");
        } else if (summary.isEveningTravel()) {
            sb.append(" Evening: slightly elevated risk.");
        }

        sb.append(" ~").append(String.format("%.0f", durationMin)).append(" min.");
        return sb.toString();
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) try { return Double.parseDouble(o.toString()); } catch (Exception e) { }
        return 0;
    }

    /** Segment-level risk for one point (normalized 0–100). */
    public double segmentRiskScore(double lat, double lng, String time) {
        double totalZoneRisk = 0;
        for (RiskZone zone : riskZones) {
            double distance = haversine(lat, lng, zone.getLatitude(), zone.getLongitude());
            if (distance <= zone.getRadius()) {
                totalZoneRisk += zone.getBaseRisk();
            }
        }
        double normalized = maxPossibleRisk > 0 ? (totalZoneRisk / maxPossibleRisk) * 100 : 0;
        if (totalZoneRisk == 0) normalized += 18; // remote/deserted segment
        if (time != null && !time.isEmpty()) {
            try {
                LocalTime userTime = LocalTime.parse(time);
                if (userTime.isAfter(LocalTime.of(23, 0)) || userTime.isBefore(LocalTime.of(4, 0))) {
                    normalized += 28;
                } else if (userTime.isAfter(LocalTime.of(20, 0))) {
                    normalized += 14;
                }
            } catch (Exception ignored) {}
        }
        return Math.min(normalized, 100);
    }

    /** riskLevel from score: SAFE / LOW / MODERATE / HIGH */
    public String scoreToRiskLevel(double score) {
        if (score < 25) return "SAFE";
        if (score < 40) return "LOW";
        if (score <= 70) return "MODERATE";
        return "HIGH";
    }

    /** Route-level risk level (<40 LOW, 40–70 MODERATE, >70 HIGH). */
    public String routeRiskLevel(double riskScore) {
        if (riskScore < 40) return "LOW";
        if (riskScore <= 70) return "MODERATE";
        return "HIGH";
    }

    public List<RouteSegmentDTO> getRouteSegments(
            List<List<Double>> routeCoordinates,
            String time
    ) {
        List<RouteSegmentDTO> segments = new ArrayList<>();
        if (routeCoordinates == null || routeCoordinates.size() < 2) {
            return segments;
        }

        for (int i = 0; i < routeCoordinates.size() - 1; i++) {
            List<Double> a = routeCoordinates.get(i);
            List<Double> b = routeCoordinates.get(i + 1);
            if (a == null || b == null || a.size() < 2 || b.size() < 2) continue;
            double a0 = toDouble(a.get(0)), a1 = toDouble(a.get(1));
            double b0 = toDouble(b.get(0)), b1 = toDouble(b.get(1));
            double midLng = (a0 + b0) / 2;
            double midLat = (a1 + b1) / 2;

            double score = segmentRiskScore(midLat, midLng, time);
            String riskLevel = scoreToRiskLevel(score);

            segments.add(new RouteSegmentDTO(
                    List.of(List.of(a0, a1), List.of(b0, b1)),
                    riskLevel
            ));
        }
        return segments;
    }

    public String getDescriptionForRiskLevel(String riskLevel) {
        if (riskLevel == null) return "";
        return switch (riskLevel) {
            case "LOW" -> "Minimizes exposure to high-risk zones.";
            case "MODERATE" -> "Passes through moderate urban risk clusters or remote stretches.";
            case "HIGH" -> "Intersects high-risk areas, remote/deserted zones, or night travel. Caution advised.";
            default -> "";
        };
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
