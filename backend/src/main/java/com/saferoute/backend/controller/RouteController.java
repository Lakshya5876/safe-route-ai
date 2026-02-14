package com.saferoute.backend.controller;

import com.saferoute.backend.model.RouteOptionDTO;
import com.saferoute.backend.model.RouteRequest;
import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.service.ORSRoutingService;
import com.saferoute.backend.service.RiskEngineService;
import com.saferoute.backend.service.RouteDeduplicationService;
import com.saferoute.backend.service.RouteDescriptionGenerator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class RouteController {

    private final RiskEngineService riskEngineService;
    private final ORSRoutingService orsRoutingService;
    private final RouteDeduplicationService routeDeduplicationService;
    private final RouteDescriptionGenerator routeDescriptionGenerator;

    public RouteController(
            RiskEngineService riskEngineService,
            ORSRoutingService orsRoutingService,
            RouteDeduplicationService routeDeduplicationService,
            RouteDescriptionGenerator routeDescriptionGenerator
    ) {
        this.riskEngineService = riskEngineService;
        this.orsRoutingService = orsRoutingService;
        this.routeDeduplicationService = routeDeduplicationService;
        this.routeDescriptionGenerator = routeDescriptionGenerator;
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
            if (ors == null || ors.coordinates == null || ors.coordinates.size() < 2) continue;
            try {
                RouteRiskSummary summary = riskEngineService.calculateRouteRiskWithSummary(ors.coordinates, time, mode);
                double durationMin = ors.durationSeconds / 60.0;

                RouteOptionDTO dto = new RouteOptionDTO();
                dto.setId("route-" + idx);
                dto.setCoordinates(ors.coordinates);
                dto.setDuration(durationMin);
                dto.setRiskScore(summary.getRiskScore());
                dto.setRiskLevel(summary.getRiskLevel());
                dto.setSegments(riskEngineService.getRouteSegments(ors.coordinates, time, mode));
                dto.setRiskSummary(summary);
                dto.setPrimary(false);
                routeOptions.add(dto);
                idx++;
            } catch (Exception e) {
                System.err.println("Skipping route " + idx + ": " + e.getMessage());
            }
        }

        if (routeOptions.isEmpty()) {
            errBody.put("error", "Could not process route data. Please try again.");
            return ResponseEntity.ok().body(errBody);
        }

        routeOptions = routeDeduplicationService.filterDuplicateRoutes(routeOptions);
        if (routeOptions.isEmpty()) {
            errBody.put("error", "Could not process route data. Please try again.");
            return ResponseEntity.ok().body(errBody);
        }

        routeOptions.sort(Comparator.comparingDouble(RouteOptionDTO::getRiskScore));
        routeOptions.get(0).setPrimary(true);

        for (int i = 0; i < routeOptions.size(); i++) {
            RouteOptionDTO dto = routeOptions.get(i);
            dto.setId("route-" + i);
        }

        List<RouteRiskSummary> allSummaries = routeOptions.stream()
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
        return ResponseEntity.ok(response);
    }
}
