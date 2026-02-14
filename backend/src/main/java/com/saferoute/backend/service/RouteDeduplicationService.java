package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteOptionDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes route options that are structurally too similar (e.g. ORS returning near-identical alternatives).
 * Comparison is by average deviation between corresponding sampled points; no hardcoded route count.
 */
@Service
public class RouteDeduplicationService {

    private static final double DEFAULT_THRESHOLD_METERS = 50.0;
    private static final int SAMPLE_POINTS = 50;

    /**
     * Filter out routes that are too similar to a better option. When two routes are duplicates,
     * keeps the one with lower (better) risk score. Result size is dynamic (1 to N).
     */
    public List<RouteOptionDTO> filterDuplicateRoutes(
            List<RouteOptionDTO> routes,
            double thresholdMeters
    ) {
        if (routes == null || routes.size() <= 1) return routes == null ? new ArrayList<>() : new ArrayList<>(routes);

        List<RouteOptionDTO> result = new ArrayList<>(routes);
        boolean[] remove = new boolean[result.size()];

        for (int i = 0; i < result.size(); i++) {
            if (remove[i]) continue;
            RouteOptionDTO a = result.get(i);
            List<List<Double>> coordsA = a.getCoordinates();
            if (coordsA == null || coordsA.size() < 2) continue;

            for (int j = i + 1; j < result.size(); j++) {
                if (remove[j]) continue;
                RouteOptionDTO b = result.get(j);
                List<List<Double>> coordsB = b.getCoordinates();
                if (coordsB == null || coordsB.size() < 2) continue;

                double avgM = averageDeviationMeters(coordsA, coordsB);
                if (avgM >= thresholdMeters) continue;

                double scoreA = a.getRiskScore();
                double scoreB = b.getRiskScore();
                if (scoreA <= scoreB) {
                    remove[j] = true;
                } else {
                    remove[i] = true;
                    break;
                }
            }
        }

        List<RouteOptionDTO> filtered = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (!remove[i]) filtered.add(result.get(i));
        }
        return filtered;
    }

    public List<RouteOptionDTO> filterDuplicateRoutes(List<RouteOptionDTO> routes) {
        return filterDuplicateRoutes(routes, DEFAULT_THRESHOLD_METERS);
    }

    /**
     * Sample corresponding points along both paths (0..1) and return average distance in meters.
     */
    public double averageDeviationMeters(List<List<Double>> coordsA, List<List<Double>> coordsB) {
        if (coordsA == null || coordsB == null || coordsA.size() < 2 || coordsB.size() < 2) {
            return Double.MAX_VALUE;
        }
        double sumM = 0;
        int count = 0;
        for (int k = 0; k < SAMPLE_POINTS; k++) {
            double t = (SAMPLE_POINTS == 1) ? 0.5 : (double) k / (SAMPLE_POINTS - 1);
            double[] ptA = pointAt(coordsA, t);
            double[] ptB = pointAt(coordsB, t);
            if (ptA == null || ptB == null) continue;
            sumM += haversineMeters(ptA[0], ptA[1], ptB[0], ptB[1]);
            count++;
        }
        return count == 0 ? Double.MAX_VALUE : sumM / count;
    }

    /** Interpolate point along polyline; t in [0,1]. Returns [lat, lng] or null. */
    private static double[] pointAt(List<List<Double>> coords, double t) {
        if (coords == null || coords.isEmpty()) return null;
        if (coords.size() == 1) {
            List<Double> p = coords.get(0);
            return p != null && p.size() >= 2 ? new double[]{ toDouble(p.get(1)), toDouble(p.get(0)) } : null;
        }
        int n = coords.size() - 1;
        double idx = t * n;
        int i = (int) Math.min(Math.floor(idx), n - 1);
        int j = i + 1;
        double frac = idx - i;
        List<Double> a = coords.get(i);
        List<Double> b = coords.get(j);
        if (a == null || b == null || a.size() < 2 || b.size() < 2) return null;
        double lat = toDouble(a.get(1)) + frac * (toDouble(b.get(1)) - toDouble(a.get(1)));
        double lng = toDouble(a.get(0)) + frac * (toDouble(b.get(0)) - toDouble(a.get(0)));
        return new double[]{ lat, lng };
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) try { return Double.parseDouble(o.toString()); } catch (Exception ignored) { }
        return 0;
    }
}
