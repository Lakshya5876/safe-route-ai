package com.saferoute.backend.controller;

import com.saferoute.backend.model.RouteOptionDTO;
import com.saferoute.backend.model.RouteRequest;
import com.saferoute.backend.service.ORSRoutingService;
import com.saferoute.backend.service.RiskModelService;
import com.saferoute.backend.service.RouteDeduplicationService;
import com.saferoute.backend.service.RouteDescriptionGenerator;
import com.saferoute.backend.service.RouteSelectionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class RouteController {

    /** Only issue a risk-avoidance re-route if the worst point on the best direct
     *  candidate is genuinely bad — avoids wasting an ORS call chasing noise. */
    private static final double AVOIDANCE_TRIGGER_RISK = 55.0;
    private static final double AVOIDANCE_RADIUS_METERS = 300.0;

    private final RiskModelService riskModelService;
    private final ORSRoutingService orsRoutingService;
    private final RouteDeduplicationService routeDeduplicationService;
    private final RouteDescriptionGenerator routeDescriptionGenerator;
    private final RouteSelectionService routeSelectionService;

    public RouteController(
            RiskModelService riskModelService,
            ORSRoutingService orsRoutingService,
            RouteDeduplicationService routeDeduplicationService,
            RouteDescriptionGenerator routeDescriptionGenerator,
            RouteSelectionService routeSelectionService
    ) {
        this.riskModelService = riskModelService;
        this.orsRoutingService = orsRoutingService;
        this.routeDeduplicationService = routeDeduplicationService;
        this.routeDescriptionGenerator = routeDescriptionGenerator;
        this.routeSelectionService = routeSelectionService;
    }

    @GetMapping("/cities")
    public ResponseEntity<Map<String, Object>> getCities() {
        Map<String, Object> body = new HashMap<>();
        body.put("cities", riskModelService.supportedCities());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/route")
    public ResponseEntity<Map<String, Object>> getRoute(
            @RequestBody RouteRequest request
    ) {
        Map<String, Object> errBody = new HashMap<>();
        errBody.put("routes", new ArrayList<>());

        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            errBody.put("error", "Origin and destination are required.");
            return ResponseEntity.badRequest().body(errBody);
        }

        String mode = request.getMode() != null ? request.getMode() : "DRIVING";
        String time = request.getTime() != null ? request.getTime() : "14:00";
        String city = request.getCity() != null ? request.getCity().toLowerCase() : null;

        if (city == null || !riskModelService.isCitySupported(city)) {
            errBody.put("error", "A supported city is required. Supported cities: "
                    + String.join(", ", riskModelService.supportedCities()));
            return ResponseEntity.badRequest().body(errBody);
        }

        List<ORSRoutingService.ORSRouteResult> orsRoutes;
        try {
            orsRoutes = orsRoutingService.getMultipleRoutes(
                    request.getOrigin().getLat(),
                    request.getOrigin().getLng(),
                    request.getDestination().getLat(),
                    request.getDestination().getLng(),
                    mode
            );
        } catch (Exception e) {
            errBody.put("error", "Routing service error. Please try again or check origin/destination.");
            return ResponseEntity.ok().body(errBody);
        }

        if (orsRoutes == null || orsRoutes.isEmpty()) {
            errBody.put("error", "No route found between these points for " + mode + ". Try different locations or mode.");
            return ResponseEntity.ok().body(errBody);
        }

        List<RouteOptionDTO> routeOptions = new ArrayList<>();
        int idx = 0;
        for (ORSRoutingService.ORSRouteResult ors : orsRoutes) {
            RouteOptionDTO dto = buildOption(ors, city, time, mode, "route-" + idx, "ORS_DIRECT");
            if (dto != null) { routeOptions.add(dto); idx++; }
        }

        if (routeOptions.isEmpty()) {
            errBody.put("error", "Could not process route data. Please try again.");
            return ResponseEntity.ok().body(errBody);
        }

        // Risk-aware candidate generation: if the best candidate so far crosses a
        // genuinely high-risk, well-confirmed point, ask ORS for a route that
        // actively avoids that specific area, instead of only re-ranking what
        // ORS's time-optimizer happened to already return.
        RouteOptionDTO bestSoFar = routeOptions.stream()
                .min(Comparator.comparingDouble(RouteOptionDTO::getRiskScore))
                .orElse(null);
        if (bestSoFar != null && bestSoFar.getRiskSummary() != null
                && bestSoFar.getRiskSummary().isHasPeak()
                && bestSoFar.getRiskSummary().getPeakRiskScore() >= AVOIDANCE_TRIGGER_RISK) {
            try {
                var peak = bestSoFar.getRiskSummary();
                List<ORSRoutingService.ORSRouteResult> avoidResults = orsRoutingService.getRouteAvoidingArea(
                        request.getOrigin().getLat(), request.getOrigin().getLng(),
                        request.getDestination().getLat(), request.getDestination().getLng(),
                        mode, peak.getPeakLat(), peak.getPeakLon(), AVOIDANCE_RADIUS_METERS
                );
                for (ORSRoutingService.ORSRouteResult ors : avoidResults) {
                    RouteOptionDTO dto = buildOption(ors, city, time, mode, "route-" + idx, "ORS_RISK_AVOIDANCE");
                    if (dto != null) { routeOptions.add(dto); idx++; }
                }
            } catch (Exception e) {
                System.err.println("Risk-avoidance routing skipped: " + e.getMessage());
            }
        }

        routeOptions = routeDeduplicationService.filterDuplicateRoutes(routeOptions);
        if (routeOptions.isEmpty()) {
            errBody.put("error", "Could not process route data. Please try again.");
            return ResponseEntity.ok().body(errBody);
        }

        routeOptions.sort(Comparator.comparingDouble(RouteOptionDTO::getRiskScore));
        routeSelectionService.markParetoOptimal(routeOptions);
        RouteOptionDTO primary = routeSelectionService.choosePrimary(routeOptions);
        for (RouteOptionDTO r : routeOptions) r.setPrimary(r == primary);

        for (int i = 0; i < routeOptions.size(); i++) {
            routeOptions.get(i).setId("route-" + i);
        }

        List<com.saferoute.backend.model.RouteRiskSummary> allSummaries = routeOptions.stream()
                .map(RouteOptionDTO::getRiskSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (int i = 0; i < routeOptions.size(); i++) {
            RouteOptionDTO dto = routeOptions.get(i);
            String desc = routeDescriptionGenerator.build(
                    dto.getRiskSummary(),
                    dto.getDuration(),
                    dto.getSegments(),
                    allSummaries,
                    i
            );
            dto.setDescription(desc);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("routes", routeOptions);
        response.put("city", city);
        return ResponseEntity.ok(response);
    }

    private RouteOptionDTO buildOption(
            ORSRoutingService.ORSRouteResult ors, String city, String time, String mode,
            String id, String generationMethod
    ) {
        if (ors == null || ors.coordinates == null || ors.coordinates.size() < 2) return null;
        try {
            RiskModelService.RouteRiskResult result = riskModelService.evaluateRoute(city, ors.coordinates, time, mode);
            double durationMin = ors.durationSeconds / 60.0;

            RouteOptionDTO dto = new RouteOptionDTO();
            dto.setId(id);
            dto.setCoordinates(ors.coordinates);
            dto.setDuration(durationMin);
            dto.setDistanceKm(result.summary.getTotalLengthKm());
            dto.setRiskScore(result.summary.getRiskScore());
            dto.setRiskLevel(result.summary.getRiskLevel());
            dto.setConfidence(result.summary.getConfidence());
            dto.setCoverageTier(result.summary.getCoverageTier());
            dto.setTotalExposureIndex(result.summary.getTotalExposureIndex());
            dto.setSegments(result.segments);
            dto.setRiskSummary(result.summary);
            dto.setPrimary(false);
            dto.setGenerationMethod(generationMethod);
            return dto;
        } catch (Exception e) {
            System.err.println("Skipping route " + id + ": " + e.getMessage());
            return null;
        }
    }
}
