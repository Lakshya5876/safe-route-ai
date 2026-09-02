package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteOptionDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteSelectionServiceTest {

    private final RouteSelectionService service = new RouteSelectionService();

    private static RouteOptionDTO option(String id, double risk, double duration) {
        RouteOptionDTO dto = new RouteOptionDTO();
        dto.setId(id);
        dto.setRiskScore(risk);
        dto.setDuration(duration);
        return dto;
    }

    @Test
    void aRouteThatIsBothSaferAndFasterDominatesAndTheOtherIsNotParetoOptimal() {
        RouteOptionDTO best = option("best", 10, 15);
        RouteOptionDTO worse = option("worse", 40, 25);
        service.markParetoOptimal(List.of(best, worse));

        assertTrue(best.isParetoOptimal());
        assertFalse(worse.isParetoOptimal());
    }

    @Test
    void routesOnTheTradeoffFrontierAreAllParetoOptimal() {
        RouteOptionDTO fastRisky = option("fast-risky", 80, 10);
        RouteOptionDTO balanced = option("balanced", 40, 15);
        RouteOptionDTO slowSafe = option("slow-safe", 5, 30);
        service.markParetoOptimal(List.of(fastRisky, balanced, slowSafe));

        assertTrue(fastRisky.isParetoOptimal(), "fastest route with no faster-or-safer alternative should be Pareto-optimal");
        assertTrue(balanced.isParetoOptimal());
        assertTrue(slowSafe.isParetoOptimal(), "safest route with no safer-or-faster alternative should be Pareto-optimal");
    }

    @Test
    void twoMinutesSlowerForDramaticallySaferIsPreferredOverTheFastestOption() {
        // Directly encodes the spec's example: "A route that is 2 minutes
        // slower but dramatically safer should be distinguishable from a route
        // that is 25 minutes slower for negligible safety improvement."
        RouteOptionDTO fastest = option("fastest", 80, 20);
        RouteOptionDTO slightlySlowerMuchSafer = option("slightly-slower", 15, 22); // +2 min, -65 risk

        RouteOptionDTO primary = service.choosePrimary(List.of(fastest, slightlySlowerMuchSafer));
        assertEquals("slightly-slower", primary.getId(),
                "a modest, bounded time cost for a large safety gain should be preferred");
    }

    @Test
    void twentyFiveMinutesSlowerForNegligibleSafetyGainIsRejected() {
        RouteOptionDTO fastest = option("fastest", 50, 20);
        RouteOptionDTO muchSlowerBarelySafer = option("much-slower", 48, 45); // +25 min, -2 risk

        RouteOptionDTO primary = service.choosePrimary(List.of(fastest, muchSlowerBarelySafer));
        assertEquals("fastest", primary.getId(),
                "an unbounded time cost for a negligible safety gain must not be selected as primary");
    }

    @Test
    void singleCandidateIsAlwaysPrimaryAndParetoOptimal() {
        RouteOptionDTO only = option("only", 50, 20);
        service.markParetoOptimal(List.of(only));
        assertTrue(only.isParetoOptimal());
        assertEquals("only", service.choosePrimary(List.of(only)).getId());
    }

    @Test
    void emptyListDoesNotThrowAndReturnsNull() {
        assertNull(service.choosePrimary(List.of()));
    }
}
