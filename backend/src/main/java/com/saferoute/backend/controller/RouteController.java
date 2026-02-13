package com.saferoute.backend.controller;

import com.saferoute.backend.model.RouteOptionDTO;
import com.saferoute.backend.model.RouteRequest;
import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.service.ORSRoutingService;
import com.saferoute.backend.service.RiskEngineService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class RouteController {

    private final RiskEngineService riskEngineService;
    private final ORSRoutingService orsRoutingService;

    public RouteController(
            RiskEngineService riskEngineService,
            ORSRoutingService orsRoutingService
    ) {
        this.riskEngineService = riskEngineService;
        this.orsRoutingService = orsRoutingService;
    }

    @PostMapping("/route")
    public ResponseEntity<Map<String, Object>> getRoute(
            @RequestBody RouteRequest request
    ) {
        if (request == null || request.getOrigin() == null || request.getDestination() == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("routes", new ArrayList<>());
            err.put("error", "Origin and destination are required.");
            return ResponseEntity.badRequest().body(err);
        }

        List<ORSRoutingService.ORSRouteResult> orsRoutes =
                orsRoutingService.getMultipleRoutes(
                        request.getOrigin().getLat(),
                        request.getOrigin().getLng(),
                        request.getDestination().getLat(),
                        request.getDestination().getLng()
                );

        if (orsRoutes.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("routes", new ArrayList<>());
            errorResponse.put("error", "No route found between these points.");
            return ResponseEntity.ok().body(errorResponse);
        }

        // Step A: Confirm ORS returned distinct routes (no duplication before scoring)
        for (int i = 0; i < orsRoutes.size(); i++) {
            ORSRoutingService.ORSRouteResult r = orsRoutes.get(i);
            int coordCount = r.coordinates != null ? r.coordinates.size() : 0;
            String start = coordCount >= 1 ? String.format("%.4f,%.4f", r.coordinates.get(0).get(0), r.coordinates.get(0).get(1)) : "?";
            String end = coordCount >= 1 ? String.format("%.4f,%.4f", r.coordinates.get(coordCount - 1).get(0), r.coordinates.get(coordCount - 1).get(1)) : "?";
            System.out.printf("[ORS] route %d: coords=%d durationSec=%.0f start=%s end=%s%n", i, coordCount, r.durationSeconds, start, end);
        }

        String time = request.getTime() != null ? request.getTime() : "14:00";

        List<RouteOptionDTO> routeOptions = new ArrayList<>();
        int idx = 0;
        for (ORSRoutingService.ORSRouteResult ors : orsRoutes) {
            if (ors == null || ors.coordinates == null || ors.coordinates.size() < 2) continue;
            try {
                RouteRiskSummary summary = riskEngineService.calculateRouteRiskWithSummary(ors.coordinates, time);
                double durationMin = ors.durationSeconds / 60.0;

                RouteOptionDTO dto = new RouteOptionDTO();
                dto.setId("route-" + idx);
                dto.setCoordinates(ors.coordinates);
                dto.setDuration(durationMin);
                dto.setRiskScore(summary.getRiskScore());
                dto.setRiskLevel(summary.getRiskLevel());
                dto.setSegments(riskEngineService.getRouteSegments(ors.coordinates, time));
                dto.setDescription(riskEngineService.buildRouteDescription(summary, durationMin));
                dto.setPrimary(false);
                routeOptions.add(dto);
                idx++;
            } catch (Exception e) {
                System.err.println("Skipping route " + idx + ": " + e.getMessage());
            }
        }

        if (routeOptions.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("routes", new ArrayList<>());
            err.put("error", "Could not process route data. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }

        routeOptions.sort(Comparator.comparingDouble(RouteOptionDTO::getRiskScore));
        if (!routeOptions.isEmpty()) {
            routeOptions.get(0).setPrimary(true);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("routes", routeOptions);
        return ResponseEntity.ok(response);
    }
}
