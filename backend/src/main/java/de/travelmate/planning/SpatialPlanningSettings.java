package de.travelmate.planning;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SpatialPlanningSettings {
    @ConfigProperty(name = "travelmate.spatial.cluster-radius-km", defaultValue = "2.0")
    double clusterRadiusKm = 2.0;

    @ConfigProperty(name = "travelmate.spatial.center-dominance-radius-km", defaultValue = "3.0")
    double centerDominanceRadiusKm = 3.0;

    @ConfigProperty(name = "travelmate.spatial.max-dominant-cluster-share", defaultValue = "0.55")
    double maxDominantClusterShare = 0.55;

    @ConfigProperty(name = "travelmate.spatial.min-expected-clusters-for-long-trip", defaultValue = "3")
    int minExpectedClustersForLongTrip = 3;

    @ConfigProperty(name = "travelmate.spatial.long-trip-day-threshold", defaultValue = "5")
    int longTripDayThreshold = 5;

    @ConfigProperty(name = "travelmate.spatial.max-consecutive-days-same-area", defaultValue = "2")
    int maxConsecutiveDaysSameArea = 2;

    @ConfigProperty(name = "travelmate.spatial.min-day-areas-two-day-trip", defaultValue = "2")
    int minDayAreasTwoDayTrip = 2;

    @ConfigProperty(name = "travelmate.spatial.min-day-areas-medium-trip", defaultValue = "2")
    int minDayAreasMediumTrip = 2;

    @ConfigProperty(name = "travelmate.spatial.min-day-areas-long-trip", defaultValue = "3")
    int minDayAreasLongTrip = 3;

    @ConfigProperty(name = "travelmate.spatial.min-day-areas-very-long-trip", defaultValue = "4")
    int minDayAreasVeryLongTrip = 4;

    @ConfigProperty(name = "travelmate.spatial.day-area-min-candidates", defaultValue = "2")
    int dayAreaMinCandidates = 2;

    @ConfigProperty(name = "travelmate.spatial.day-area-anchor-min-score", defaultValue = "6.5")
    double dayAreaAnchorMinScore = 6.5;

    @ConfigProperty(name = "travelmate.spatial.day-area-require-food-option", defaultValue = "true")
    boolean dayAreaRequireFoodOption = true;

    @ConfigProperty(name = "travelmate.spatial.day-area-food-radius-factor", defaultValue = "1.0")
    double dayAreaFoodRadiusFactor = 1.0;

    @ConfigProperty(name = "travelmate.spatial.max-same-interest-per-day", defaultValue = "2")
    int maxSameInterestPerDay = 2;

    public double clusterRadiusKm() {
        return Math.max(0.2, clusterRadiusKm);
    }

    public double centerDominanceRadiusKm() {
        return Math.max(0.5, centerDominanceRadiusKm);
    }

    public double maxDominantClusterShare() {
        return Math.max(0.1, Math.min(0.95, maxDominantClusterShare));
    }

    public int minExpectedClustersForLongTrip() {
        return Math.max(1, minExpectedClustersForLongTrip);
    }

    public int longTripDayThreshold() {
        return Math.max(2, longTripDayThreshold);
    }

    public int maxConsecutiveDaysSameArea() {
        return Math.max(1, maxConsecutiveDaysSameArea);
    }

    public int targetDistinctAreasForTrip(int tripDays) {
        if (tripDays <= 1) return 1;
        if (tripDays == 2) return Math.max(1, minDayAreasTwoDayTrip);
        if (tripDays <= 4) return Math.max(1, minDayAreasMediumTrip);
        if (tripDays <= 7) return Math.max(1, minDayAreasLongTrip);
        return Math.max(1, minDayAreasVeryLongTrip);
    }

    public int dayAreaMinCandidates() {
        return Math.max(1, dayAreaMinCandidates);
    }

    public double dayAreaAnchorMinScore() {
        return Math.max(0, dayAreaAnchorMinScore);
    }

    public boolean dayAreaRequireFoodOption() {
        return dayAreaRequireFoodOption;
    }

    public double dayAreaFoodRadiusFactor() {
        return Math.max(0.25, dayAreaFoodRadiusFactor);
    }

    public int maxSameInterestPerDay() {
        return Math.max(1, maxSameInterestPerDay);
    }
}
