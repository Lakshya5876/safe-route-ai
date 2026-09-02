package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.model.RouteSegmentDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Builds a human-readable explanation of a route's score. Rewritten against
 * the new coherent risk model: there are no more named "zones" (the old
 * model's 16 hand-typed circles), so this now explains risk, confidence, and
 * data coverage — including explicitly saying so when data is too sparse to
 * trust the score, which the old version never did.
 */
@Service
public class RouteDescriptionGenerator {

    public String build(
            RouteRiskSummary summary,
            double durationMin,
            List<RouteSegmentDTO> segments,
            List<RouteRiskSummary> allSummaries,
            int thisIndex
    ) {
        if (summary == null) return "Route overview.";
        StringBuilder sb = new StringBuilder();

        if ("UNKNOWN".equals(summary.getRiskLevel()) || "insufficient".equals(summary.getCoverageTier())) {
            sb.append("Insufficient safety data for this area — risk score is not meaningful here.");
            sb.append(" ~").append(String.format("%.0f", durationMin)).append(" min.");
            return sb.toString();
        }

        double risk = summary.getRiskScore();
        double minRisk = allSummaries.stream().mapToDouble(RouteRiskSummary::getRiskScore).min().orElse(risk);
        double maxRisk = allSummaries.stream().mapToDouble(RouteRiskSummary::getRiskScore).max().orElse(risk);
        long saferCount = allSummaries.stream().filter(s -> s.getRiskScore() < risk).count();
        boolean isSafest = risk <= minRisk;
        boolean isRiskiest = risk >= maxRisk;

        double safetyPct = Math.max(0, 100 - risk);
        if (safetyPct >= 70) {
            sb.append("Low relative risk for this city.");
        } else if (safetyPct >= 45) {
            sb.append("Moderate relative risk.");
        } else {
            sb.append("Elevated relative risk; consider alternatives if available.");
        }

        if (allSummaries.size() > 1) {
            if (isSafest) {
                sb.append(" Safest of ").append(allSummaries.size()).append(" options.");
            } else if (isRiskiest) {
                sb.append(" Highest risk of the ").append(allSummaries.size()).append(" options.");
            } else {
                sb.append(" ").append((int) saferCount).append(" option(s) score lower risk.");
            }
        }

        switch (summary.getCoverageTier()) {
            case "high" -> sb.append(" Based on good local data coverage.");
            case "moderate" -> sb.append(" Based on moderate local data coverage.");
            case "sparse" -> sb.append(" Local data coverage is sparse — treat this score as approximate.");
            default -> sb.append(" Data coverage is very limited here — treat this score cautiously.");
        }

        if (summary.isNightTravel()) {
            sb.append(" Night travel: lighting and isolation risk weighted higher.");
        } else if (summary.isEveningTravel()) {
            sb.append(" Evening: lighting and isolation risk weighted slightly higher.");
        }

        sb.append(" ~").append(String.format("%.0f", durationMin)).append(" min.");
        return sb.toString();
    }
}
