package com.saferoute.backend.controller;

import com.saferoute.backend.model.*;
import com.saferoute.backend.service.*;

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
    public Map<String, Object> getRoute(
            @RequestBody RouteRequest request
    ) {

        List<List<Double>> coordinates =
                orsRoutingService.getRouteCoordinates(
                        request.getOrigin().getLat(),
                        request.getOrigin().getLng(),
                        request.getDestination().getLat(),
                        request.getDestination().getLng()
                );

        if (coordinates.isEmpty()) {

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("route", new ArrayList<>());
            errorResponse.put("safetyScore", 0);
            errorResponse.put("riskLevel", "UNAVAILABLE");
            errorResponse.put("alerts", new ArrayList<>());
            return errorResponse;
        }

        double riskScore =
                riskEngineService.calculateRouteRisk(
                        coordinates,
                        request.getTime()
                );

        List<RiskBreakdown> breakdown =
                riskEngineService.getRouteBreakdown(
                        coordinates,
                        request.getTime()
                );

        String riskLevel =
                riskScore >= 70 ? "HIGH"
                : riskScore >= 40 ? "MODERATE"
                : "LOW";

        Map<String, Object> response = new HashMap<>();
        response.put("route", coordinates);
        response.put("safetyScore", riskScore);
        response.put("riskLevel", riskLevel);
        response.put("alerts", breakdown);

        return response;
    }
}
