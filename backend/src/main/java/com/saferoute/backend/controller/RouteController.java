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

        
        double riskScore = riskEngineService.calculateRisk(request.getTime());

        
        List<RiskBreakdown> breakdown = riskEngineService.getBreakdown(request.getTime());

        
        String riskLevel;
        if (riskScore >= 40) {
            riskLevel = "High";
        } else if (riskScore >= 25) {
            riskLevel = "Moderate";
        } else {
            riskLevel = "Low";
        }

        
        return new RouteResponse(
                "dummy_polyline_string",
                riskScore,
                riskLevel,
                breakdown
        );
    }
}
