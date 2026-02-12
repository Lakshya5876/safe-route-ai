package com.saferoute.backend.service;

import com.saferoute.backend.model.RiskBreakdown;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskEngineService {

    public double calculateRisk(String time) {

        double baseRisk = 20; // Base crime zone risk

        if (time != null && !time.isEmpty()) {

            LocalTime userTime = LocalTime.parse(time);

            // Late night high risk (11 PM – 4 AM)
            if (userTime.isAfter(LocalTime.of(23, 0)) || userTime.isBefore(LocalTime.of(4, 0))) {
                baseRisk += 20;
            }
            // Evening moderate risk (8 PM – 11 PM)
            else if (userTime.isAfter(LocalTime.of(20, 0))) {
                baseRisk += 10;
            }
        }

        return baseRisk;
    }

    public List<RiskBreakdown> getBreakdown(String time) {

        List<RiskBreakdown> breakdown = new ArrayList<>();

        breakdown.add(new RiskBreakdown("Crime Zone", 20));

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
