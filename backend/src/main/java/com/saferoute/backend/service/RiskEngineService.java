package com.saferoute.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.backend.model.RiskBreakdown;
import com.saferoute.backend.model.RiskZone;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskEngineService {

    private List<RiskZone> riskZones = new ArrayList<>();

    @PostConstruct
    public void loadRiskData() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = getClass().getResourceAsStream("/risk-data.json");
            riskZones = mapper.readValue(inputStream, new TypeReference<List<RiskZone>>() {});
            System.out.println("Risk data loaded: " + riskZones.size() + " zones");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public double calculateRisk(String time) {

        double baseRisk = riskZones.stream()
                .mapToDouble(RiskZone::getBaseRisk)
                .average()
                .orElse(20);

        if (time != null && !time.isEmpty()) {

            LocalTime userTime = LocalTime.parse(time);

            if (userTime.isAfter(LocalTime.of(23, 0)) || userTime.isBefore(LocalTime.of(4, 0))) {
                baseRisk += 20;
            } else if (userTime.isAfter(LocalTime.of(20, 0))) {
                baseRisk += 10;
            }
        }

        return baseRisk;
    }

    public List<RiskBreakdown> getBreakdown(String time) {

        List<RiskBreakdown> breakdown = new ArrayList<>();

        breakdown.add(new RiskBreakdown("Base Crime Risk (From Dataset)", 
                riskZones.stream().mapToDouble(RiskZone::getBaseRisk).average().orElse(20)));

        if (time != null && !time.isEmpty()) {

            LocalTime userTime = LocalTime.parse(time);

            if (userTime.isAfter(LocalTime.of(23, 0)) || userTime.isBefore(LocalTime.of(4, 0))) {
                breakdown.add(new RiskBreakdown("Late Night Multiplier", 20));
            } else if (userTime.isAfter(LocalTime.of(20, 0))) {
                breakdown.add(new RiskBreakdown("Evening Risk Multiplier", 10));
            }
        }

        return breakdown;
    }
}
