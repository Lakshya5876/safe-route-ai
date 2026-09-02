package com.saferoute.backend.service;

import com.saferoute.backend.model.RiskGrid;
import com.saferoute.backend.model.RiskGridCell;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 13 performance measurement: does the new grid-indexed lookup actually
 * behave as O(1) per sample point regardless of dataset size, as claimed?
 * And how much did the OLD unindexed "scan every zone for every point"
 * approach (audited previously) actually cost at realistic scale?
 *
 * This loads the REAL production grids (via the same classpath resources the
 * app uses) rather than synthetic data, and compares:
 *   (a) the new HashMap-indexed lookup (RiskModelService.evaluateRoute)
 *   (b) a reimplementation of the OLD approach's cost structure — a linear
 *       scan over every cell for every sample point — against the SAME real
 *       grids, so the comparison uses real, not invented, dataset sizes.
 *
 * Mumbai's grid (29,882 cells) and Pune's (6,375 cells) give a natural ~4.7x
 * difference in Z for free, since both are already loaded from real OSM data.
 */
class RiskModelPerformanceTest {

    private static final int ROUTE_POINTS = 500; // a long, realistic multi-km urban route

    @SuppressWarnings("unchecked")
    private static Map<String, RiskGrid> loadRealGrids() throws Exception {
        RiskModelService service = new RiskModelService();
        service.loadGrids();
        Field f = RiskModelService.class.getDeclaredField("gridsByCity");
        f.setAccessible(true);
        return (Map<String, RiskGrid>) f.get(service);
    }

    private static List<List<Double>> longSyntheticRoute(double[] bbox, int points) {
        List<List<Double>> coords = new ArrayList<>();
        double south = bbox[0], west = bbox[1], north = bbox[2], east = bbox[3];
        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            // a zig-zag path across the whole bbox, so it exercises many different cells
            double lat = south + t * (north - south);
            double lon = west + ((i % 2 == 0) ? 0.1 : 0.9) * (east - west);
            coords.add(List.of(lon, lat));
        }
        return coords;
    }

    /** Reimplements the OLD model's cost structure: scan every cell for every sample point. */
    private static long naiveLinearScanNanos(RiskGrid grid, List<List<Double>> route) {
        long start = System.nanoTime();
        int hits = 0;
        for (List<Double> pt : route) {
            double lat = pt.get(1), lon = pt.get(0);
            for (RiskGridCell cell : grid.getCells()) {
                double dLat = cell.getLat() - lat, dLon = cell.getLon() - lon;
                if (Math.sqrt(dLat * dLat + dLon * dLon) < grid.getManifest().getCellSizeLatDeg()) {
                    hits++; // force the JIT to not optimize the loop away
                }
            }
        }
        long elapsed = System.nanoTime() - start;
        if (hits < 0) throw new IllegalStateException(); // never true; keeps `hits` live
        return elapsed;
    }

    @Test
    void gridLookupTimeIsNotProportionalToCellCountWhileNaiveScanIs() throws Exception {
        Map<String, RiskGrid> grids = loadRealGrids();
        RiskGrid pune = grids.get("pune");     // 6,375 cells
        RiskGrid mumbai = grids.get("mumbai"); // 29,882 cells
        assertTrue(pune != null && mumbai != null, "requires the real pune/mumbai grids to be present on the classpath");

        double cellRatio = (double) mumbai.getCells().size() / pune.getCells().size();

        RiskModelService service = new RiskModelService();
        service.loadGridForTesting("pune", pune);
        service.loadGridForTesting("mumbai", mumbai);

        List<List<Double>> puneRoute = longSyntheticRoute(pune.getManifest().getBbox(), ROUTE_POINTS);
        List<List<Double>> mumbaiRoute = longSyntheticRoute(mumbai.getManifest().getBbox(), ROUTE_POINTS);

        // Warm up BOTH code paths for BOTH cities before measuring anything — JIT
        // compilation cost otherwise contaminates whichever measurement runs first
        // and makes the comparison meaningless noise.
        for (int i = 0; i < 30; i++) {
            service.evaluateRoute("pune", puneRoute, "14:00", "DRIVING");
            service.evaluateRoute("mumbai", mumbaiRoute, "14:00", "DRIVING");
            naiveLinearScanNanos(pune, puneRoute);
            naiveLinearScanNanos(mumbai, mumbaiRoute);
        }

        long puneGridNanos = median(() -> timeGridLookup(service, "pune", puneRoute));
        long mumbaiGridNanos = median(() -> timeGridLookup(service, "mumbai", mumbaiRoute));
        long puneNaiveNanos = median(() -> naiveLinearScanNanos(pune, puneRoute));
        long mumbaiNaiveNanos = median(() -> naiveLinearScanNanos(mumbai, mumbaiRoute));

        double gridRatio = (double) mumbaiGridNanos / puneGridNanos;
        double naiveRatio = (double) mumbaiNaiveNanos / puneNaiveNanos;

        System.out.printf(
                "%n[Performance] cellCountRatio(mumbai/pune)=%.2fx%n" +
                "[Performance] GRID lookup:  pune=%.2fms mumbai=%.2fms -> ratio=%.2fx%n" +
                "[Performance] NAIVE scan:   pune=%.2fms mumbai=%.2fms -> ratio=%.2fx%n",
                cellRatio,
                puneGridNanos / 1e6, mumbaiGridNanos / 1e6, gridRatio,
                puneNaiveNanos / 1e6, mumbaiNaiveNanos / 1e6, naiveRatio
        );

        // The grid-indexed lookup's cost should track ROUTE length (constant here), not cell
        // count — its ratio between a 4.7x-larger and smaller city should stay near 1x.
        assertTrue(gridRatio < 2.0,
                "grid-indexed lookup time should stay roughly flat across a " + String.format("%.1f", cellRatio)
                        + "x difference in cell count (got ratio " + String.format("%.2f", gridRatio) + "x)");

        // The naive scan's slowdown with dataset size should be clearly worse than the
        // grid's — a relative claim, which is robust to JIT/JVM microbenchmark noise,
        // rather than asserting an exact proportionality constant a single-shot,
        // non-JMH benchmark cannot reliably deliver.
        assertTrue(naiveRatio > gridRatio * 1.3,
                "naive linear scan should degrade with cell count meaningfully more than the indexed lookup does "
                        + "(grid ratio " + String.format("%.2f", gridRatio) + "x, naive ratio "
                        + String.format("%.2f", naiveRatio) + "x)");
    }

    private static long median(java.util.function.LongSupplier timedOp) {
        long[] samples = new long[7];
        for (int i = 0; i < samples.length; i++) samples[i] = timedOp.getAsLong();
        java.util.Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static long timeGridLookup(RiskModelService service, String city, List<List<Double>> route) {
        long start = System.nanoTime();
        service.evaluateRoute(city, route, "14:00", "DRIVING");
        return System.nanoTime() - start;
    }
}
