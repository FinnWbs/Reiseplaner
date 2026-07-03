package de.travelmate.planning;

import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpatialPlanningService {
    @Inject
    SpatialPlanningSettings settings;

    @Inject
    SpatialClusterer clusterer;

    public SpatialPlanningContext createContext(
        TripEntity trip,
        List<ScoredActivity> scored,
        List<TripDayEntity> orderedDays
    ) {
        SpatialPlanningSettings resolvedSettings = settings();
        List<SpatialCluster> clusters = clusterer().clusterScored(
            scored,
            resolvedSettings.clusterRadiusKm(),
            trip.latitude,
            trip.longitude
        );
        if (clusters.isEmpty()) {
            return SpatialPlanningContext.empty(resolvedSettings);
        }
        Map<Long, Integer> clusterByActivity = clusterer().clusterByActivityId(clusters);
        Map<Integer, SpatialCluster> clusterById = clusters.stream()
            .collect(Collectors.toMap(SpatialCluster::id, cluster -> cluster));
        List<SpatialCluster> selectedDayClusters = selectedDayClusters(trip, clusters);
        Map<Integer, Integer> preferredByDay = new LinkedHashMap<>();
        for (int index = 0; index < orderedDays.size(); index++) {
            SpatialCluster cluster = selectedDayClusters.get(index % selectedDayClusters.size());
            preferredByDay.put(orderedDays.get(index).dayNumber, cluster.id());
        }
        return new SpatialPlanningContext(clusterByActivity, clusterById, preferredByDay, resolvedSettings);
    }

    private List<SpatialCluster> selectedDayClusters(TripEntity trip, List<SpatialCluster> clusters) {
        List<SpatialCluster> ordered = clusters.stream()
            .sorted(Comparator.comparingDouble((SpatialCluster cluster) -> clusterPlanningScore(trip, cluster)).reversed()
                .thenComparingInt(SpatialCluster::id))
            .toList();
        int days = Math.max(1, trip.daysCount > 0 ? trip.daysCount : trip.days.size());
        if (days < settings().longTripDayThreshold()) {
            return ordered.stream().limit(Math.min(2, ordered.size())).toList();
        }
        int targetClusters = Math.min(
            ordered.size(),
            Math.max(settings().minExpectedClustersForLongTrip(), Math.min(4, days))
        );
        return ordered.stream().limit(Math.max(1, targetClusters)).toList();
    }

    private double clusterPlanningScore(TripEntity trip, SpatialCluster cluster) {
        double sizeScore = Math.min(0.24, cluster.activityCount() * 0.03);
        double interestVarietyScore = Math.min(0.18, cluster.interests().size() * 0.045);
        double outerClusterSignal = cluster.distanceFromCityCenterKm() > settings().centerDominanceRadiusKm() ? 0.06 : 0;
        return cluster.averageScore() + sizeScore + interestVarietyScore + outerClusterSignal;
    }

    private SpatialPlanningSettings settings() {
        return settings == null ? new SpatialPlanningSettings() : settings;
    }

    private SpatialClusterer clusterer() {
        return clusterer == null ? new SpatialClusterer() : clusterer;
    }
}
