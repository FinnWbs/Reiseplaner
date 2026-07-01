package de.travelmate.quality;

import java.util.Map;

public final class ScoreNormalizer {
    private ScoreNormalizer() {}

    public static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    public static double safeDivide(double dividend, double divisor) {
        return divisor <= 0 ? 0 : dividend / divisor;
    }

    public static double logNormalize(double value, double p95OrMax) {
        if (value <= 0 || p95OrMax <= 0) return 0;
        return clamp01(Math.log1p(value) / Math.log1p(p95OrMax));
    }

    public static double weightedSum(Map<Double, Double> weightedParts) {
        double totalWeight = weightedParts.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) return 0;
        double sum = weightedParts.entrySet().stream()
            .mapToDouble(entry -> clamp01(entry.getKey()) * entry.getValue())
            .sum();
        return clamp01(sum / totalWeight);
    }
}
