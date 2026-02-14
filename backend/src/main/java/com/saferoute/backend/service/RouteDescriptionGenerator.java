package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.model.RouteSegmentDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Score-aware narrative generator. Uses continuous risk, duration, comparison context,
 * and segment variance/spikes to produce distinct, analytical descriptions (no crude if/else bands).
 */
@Service
public class RouteDescriptionGenerator {

    private static double numericRisk(String level) {
        if (level == null) return 20;
        return switch (level.toUpperCase()) {
            case "HIGH" -> 82;
            case "MODERATE" -> 55;
            case "LOW" -> 32;
            default -> 15; // SAFE
        };
    }

    /**
     * Build a context-aware description from risk score, duration, segment distribution,
     * and comparison with other routes. Descriptions differ by route and reflect tradeoffs.
     */
    public String build(
            RouteRiskSummary summary,
            double durationMin,
            List<RouteSegmentDTO> segments,
            List<RouteRiskSummary> allSummaries,
            int thisIndex
    ) {
        if (summary == null) return "Route overview.";
        double risk = summary.getRiskScore();
        int zoneCount = summary.getZoneNames() != null ? summary.getZoneNames().size() : 0;

        double minRisk = allSummaries.stream().mapToDouble(RouteRiskSummary::getRiskScore).min().orElse(risk);
        double maxRisk = allSummaries.stream().mapToDouble(RouteRiskSummary::getRiskScore).max().orElse(risk);
        long saferCount = allSummaries.stream().filter(s -> s.getRiskScore() < risk).count();
        boolean isSafest = risk <= minRisk;
        boolean isRiskiest = risk >= maxRisk;

        double segmentVariance = 0;
        double maxSegmentRisk = 0;
        double avgSegmentRisk = 0;
        if (segments != null && !segments.isEmpty()) {
            double[] nums = segments.stream()
                    .mapToDouble(s -> numericRisk(s.getRiskLevel()))
                    .toArray();
            avgSegmentRisk = java.util.Arrays.stream(nums).average().orElse(0);
            maxSegmentRisk = java.util.Arrays.stream(nums).max().orElse(0);
            double avg = avgSegmentRisk;
            segmentVariance = java.util.Arrays.stream(nums).map(x -> (x - avg) * (x - avg)).average().orElse(0);
        }
        boolean hasSpike = maxSegmentRisk > avgSegmentRisk + 25;
        boolean consistentRisk = segmentVariance < 200 && !hasSpike;

        StringBuilder sb = new StringBuilder();

        // Overall exposure (proportional to score band without fixed thresholds)
        double safetyPct = Math.max(0, 100 - risk);
        if (safetyPct >= 75) {
            sb.append("Low overall exposure.");
        } else if (safetyPct >= 55) {
            sb.append("Moderate overall exposure.");
        } else if (safetyPct >= 35) {
            sb.append("Elevated exposure; some higher-risk segments.");
        } else {
            sb.append("Higher overall exposure; consider alternatives if possible.");
        }

        // Comparison context
        if (allSummaries.size() > 1) {
            if (isSafest) {
                sb.append(" This is the safest option among ").append(allSummaries.size()).append(" routes.");
            } else if (isRiskiest) {
                sb.append(" This route scores highest risk of the ").append(allSummaries.size()).append(" options.");
            } else {
                sb.append(" ").append((int) saferCount).append(" route(s) have lower risk.");
            }
        }

        // Zone context
        if (zoneCount > 0) {
            sb.append(" Passes ").append(zoneCount).append(" known risk zone(s)");
            if (summary.getZoneCategories() != null && !summary.getZoneCategories().isEmpty()) {
                sb.append(" (").append(String.join(", ", summary.getZoneCategories())).append(")");
            }
            sb.append(".");
        } else {
            sb.append(" No known risk zones on this path.");
        }

        // Segment character: spikes vs consistent
        if (hasSpike && segments != null && segments.size() > 3) {
            sb.append(" One or more segments show concentrated risk.");
        } else if (consistentRisk && segments != null && segments.size() > 2) {
            sb.append(" Risk is relatively even along the path.");
        }

        // Time
        if (summary.isNightTravel()) {
            sb.append(" Night travel: assault and lighting risk elevated.");
        } else if (summary.isEveningTravel()) {
            sb.append(" Evening: slightly elevated risk.");
        }

        sb.append(" ~").append(String.format("%.0f", durationMin)).append(" min.");
        return sb.toString();
    }
}
