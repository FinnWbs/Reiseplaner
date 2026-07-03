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
}
