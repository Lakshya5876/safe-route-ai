package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteOptionDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Multi-objective route selection over (risk, duration). Replaces the
 * previous implementation's pure `sort by riskScore, take the minimum`,
 * which never considered travel time at all — a route 25 minutes slower for
 * a negligible safety improvement was treated identically to one 2 minutes
 * slower for a dramatic improvement.
 *
 * Two things are computed:
 *  1. Pareto optimality: a route is Pareto-optimal if no other candidate is
 *     BOTH no riskier and no slower. All Pareto-optimal routes are marked and
 *     returned to the frontend as legitimate alternatives — the point being
 *     that a user, not a hardcoded formula, may reasonably prefer a slower,
 *     safer option or a riskier, faster one, and both should stay visible.
 *  2. A single "primary" recommendation, chosen by an explainable, bounded
 *     tradeoff rule rather than an arbitrary weighted sum of risk and
 *     duration (which would need weights nobody could justify): the primary
 *     is the LOWEST-RISK route among candidates whose duration is within a
 *     documented bound of the fastest candidate's duration. This is a
 *     concrete, defensible policy — "don't accept an arbitrarily large time
 *     cost for a safety gain, but among reasonable options, prefer safety" —
 *     not a magic score.
 */
@Service
public class RouteSelectionService {

    /** Multiplicative slack on top of the fastest route's duration. */
    private static final double DURATION_SLACK_RATIO = 1.20;
    /** Additive slack (minutes) so short trips aren't unfairly bound by a tiny percentage. */
    private static final double DURATION_SLACK_MINUTES = 2.0;

    /** Marks each route's paretoOptimal flag in place and returns the same list, for convenience. */
    public List<RouteOptionDTO> markParetoOptimal(List<RouteOptionDTO> routes) {
        for (RouteOptionDTO candidate : routes) {
            boolean dominated = routes.stream().anyMatch(other -> dominates(other, candidate));
            candidate.setParetoOptimal(!dominated);
        }
        return routes;
    }

    /** True if `a` dominates `b`: no worse on both objectives, and strictly better on at least one. */
    private boolean dominates(RouteOptionDTO a, RouteOptionDTO b) {
        if (a == b) return false;
        boolean noWorse = a.getRiskScore() <= b.getRiskScore() && a.getDuration() <= b.getDuration();
        boolean strictlyBetter = a.getRiskScore() < b.getRiskScore() || a.getDuration() < b.getDuration();
        return noWorse && strictlyBetter;
    }

    /**
     * Choose the primary recommendation using the bounded-tradeoff rule
     * described in the class doc. Assumes markParetoOptimal has already run
     * (not required, but this is where the interesting alternatives are).
     */
    public RouteOptionDTO choosePrimary(List<RouteOptionDTO> routes) {
        if (routes == null || routes.isEmpty()) return null;
        double fastestDuration = routes.stream().mapToDouble(RouteOptionDTO::getDuration).min().orElse(0);
        double maxAcceptableDuration = fastestDuration * DURATION_SLACK_RATIO + DURATION_SLACK_MINUTES;

        return routes.stream()
                .filter(r -> r.getDuration() <= maxAcceptableDuration)
                .min((a, b) -> {
                    int byRisk = Double.compare(a.getRiskScore(), b.getRiskScore());
                    return byRisk != 0 ? byRisk : Double.compare(a.getDuration(), b.getDuration());
                })
                .orElseGet(() -> routes.stream()
                        .min((a, b) -> Double.compare(a.getRiskScore(), b.getRiskScore()))
                        .orElse(routes.get(0)));
    }
}
