package com.saferoute.backend.controller;

import com.saferoute.backend.model.RouteRequest;
import com.saferoute.backend.model.RouteResponse;
import com.saferoute.backend.model.RiskBreakdown;
import com.saferoute.backend.service.RiskEngineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
@CrossOrigin(origins = "http://localhost:5173")
public class RouteController {

    private final RiskEngineService riskEngineService;

    public RouteController(RiskEngineService riskEngineService) {
        this.riskEngineService = riskEngineService;
    }

    @PostMapping("/safest")
    public RouteResponse getSafestRoute(@RequestBody RouteRequest request) {

        // Calculate total risk score
        double riskScore = riskEngineService.calculateRisk(request.getTime());

        // Get breakdown factors
        List<RiskBreakdown> breakdown = riskEngineService.getBreakdown(request.getTime());

        // Decide risk level based on score
        String riskLevel;
        if (riskScore >= 40) {
            riskLevel = "High";
        } else if (riskScore >= 25) {
            riskLevel = "Moderate";
        } else {
            riskLevel = "Low";
        }

        // Return structured response
        return new RouteResponse(
                "dummy_polyline_string",
                riskScore,
                riskLevel,
                breakdown
        );
    }
}
