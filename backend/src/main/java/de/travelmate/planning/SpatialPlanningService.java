package de.travelmate.planning;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<Long, ScoredActivity> scoredByActivityId = scoredByActivityId(scored);
        List<SpatialCluster> selectedDayClusters = selectedDayClusters(trip, clusters, scoredByActivityId);
        Map<Integer, Integer> preferredByDay = new LinkedHashMap<>();
        for (DayAreaAssignment assignment : assignDayAreas(orderedDays, selectedDayClusters)) {
            preferredByDay.put(assignment.dayNumber(), assignment.clusterId());
        }
        return new SpatialPlanningContext(clusterByActivity, clusterById, preferredByDay, resolvedSettings);
    }

    private List<SpatialCluster> selectedDayClusters(
        TripEntity trip,
        List<SpatialCluster> clusters,
        Map<Long, ScoredActivity> scoredByActivityId
    ) {
        List<SpatialCluster> ordered = clusters.stream()
            .sorted(Comparator.comparingDouble((SpatialCluster cluster) -> clusterPlanningScore(trip, cluster)).reversed()
                .thenComparingInt(SpatialCluster::id))
            .toList();
        int days = Math.max(1, trip.daysCount > 0 ? trip.daysCount : trip.days.size());
        if (days <= 1) {
            return ordered.stream().limit(1).toList();
        }
        Set<InterestType> selectedTypes = selectedTypes(trip);
        boolean foodRelevant = selectedTypes.contains(InterestType.FOOD);
        boolean anchorRequired = selectedTypes.stream().anyMatch(this::isAnchorInterest);
        List<SpatialCluster> dayReady = ordered.stream()
            .filter(cluster -> isDayReadyCluster(cluster, clusters, scoredByActivityId, foodRelevant, anchorRequired))
            .toList();
        List<SpatialCluster> source = dayReady.isEmpty() ? ordered : dayReady;
        int targetClusters = Math.min(
            source.size(),
            Math.min(days, settings().targetDistinctAreasForTrip(days))
        );
        return source.stream().limit(Math.max(1, targetClusters)).toList();
    }

    private double clusterPlanningScore(TripEntity trip, SpatialCluster cluster) {
        double sizeScore = Math.min(0.24, cluster.activityCount() * 0.03);
        double interestVarietyScore = Math.min(0.24, cluster.interests().size() * 0.06);
        double outerClusterSignal = cluster.distanceFromCityCenterKm() > settings().centerDominanceRadiusKm() ? 0.06 : 0;
        double singleInterestPenalty = cluster.interests().size() <= 1 ? 0.08 : 0;
        return cluster.averageScore() + sizeScore + interestVarietyScore + outerClusterSignal - singleInterestPenalty;
    }

    private List<DayAreaAssignment> assignDayAreas(
        List<TripDayEntity> orderedDays,
        List<SpatialCluster> selectedDayClusters
    ) {
        if (selectedDayClusters.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> consecutiveUse = new LinkedHashMap<>();
        java.util.ArrayList<DayAreaAssignment> assignments = new java.util.ArrayList<>();
        Integer previousCluster = null;
        int cursor = 0;
        for (TripDayEntity day : orderedDays) {
            SpatialCluster selected = null;
            for (int attempt = 0; attempt < selectedDayClusters.size(); attempt++) {
                SpatialCluster candidate = selectedDayClusters.get((cursor + attempt) % selectedDayClusters.size());
                int consecutive = candidate.id() == (previousCluster == null ? -1 : previousCluster)
                    ? consecutiveUse.getOrDefault(candidate.id(), 0)
                    : 0;
                if (candidate.id() != (previousCluster == null ? -1 : previousCluster)
                    || consecutive < settings().maxConsecutiveDaysSameArea()
                    || selectedDayClusters.size() == 1) {
                    selected = candidate;
                    cursor = (cursor + attempt + 1) % selectedDayClusters.size();
                    break;
                }
            }
            if (selected == null) {
                selected = selectedDayClusters.get(cursor % selectedDayClusters.size());
                cursor = (cursor + 1) % selectedDayClusters.size();
            }
            int consecutive = selected.id() == (previousCluster == null ? -1 : previousCluster)
                ? consecutiveUse.getOrDefault(selected.id(), 0) + 1
                : 1;
            consecutiveUse.clear();
            consecutiveUse.put(selected.id(), consecutive);
            previousCluster = selected.id();
            assignments.add(new DayAreaAssignment(day.dayNumber, selected.id()));
        }
        return List.copyOf(assignments);
    }

    private boolean isDayReadyCluster(
        SpatialCluster cluster,
        List<SpatialCluster> clusters,
        Map<Long, ScoredActivity> scoredByActivityId,
        boolean foodRelevant,
        boolean anchorRequired
    ) {
        if (cluster.activityCount() < settings().dayAreaMinCandidates()) {
            return false;
        }
        if (anchorRequired && !hasStrongAnchor(cluster, scoredByActivityId)) {
            return false;
        }
        return !foodRelevant || !settings().dayAreaRequireFoodOption() || hasFoodOption(cluster, clusters);
    }

    private boolean hasStrongAnchor(SpatialCluster cluster, Map<Long, ScoredActivity> scoredByActivityId) {
        return cluster.activityIds().stream()
            .map(scoredByActivityId::get)
            .filter(java.util.Objects::nonNull)
            .filter(item -> isAnchorInterest(item.activity().primaryInterest))
            .anyMatch(item -> item.totalScore() >= settings().dayAreaAnchorMinScore());
    }

    private boolean hasFoodOption(SpatialCluster cluster, List<SpatialCluster> clusters) {
        if (cluster.interests().contains(InterestType.FOOD.name())) {
            return true;
        }
        return clusters.stream()
            .filter(other -> other.interests().contains(InterestType.FOOD.name()))
            .anyMatch(other -> SpatialClusterer.distanceKm(
                cluster.centerLat(),
                cluster.centerLon(),
                other.centerLat(),
                other.centerLon()
            ) <= settings().clusterRadiusKm() * settings().dayAreaFoodRadiusFactor());
    }

    private boolean isAnchorInterest(InterestType type) {
        return type != null && type != InterestType.FOOD && type != InterestType.NIGHTLIFE;
    }

    private Set<InterestType> selectedTypes(TripEntity trip) {
        if (trip == null || trip.selectedInterests == null) {
            return Set.of();
        }
        return trip.selectedInterests.stream()
            .map(interest -> interest.code)
            .filter(java.util.Objects::nonNull)
            .map(code -> {
                try {
                    return InterestType.valueOf(code);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Map<Long, ScoredActivity> scoredByActivityId(List<ScoredActivity> scored) {
        Map<Long, ScoredActivity> result = new HashMap<>();
        for (ScoredActivity item : scored) {
            if (item.activity().id != null) {
                result.putIfAbsent(item.activity().id, item);
            }
        }
        return result;
    }

    private SpatialPlanningSettings settings() {
        return settings == null ? new SpatialPlanningSettings() : settings;
    }

    private SpatialClusterer clusterer() {
        return clusterer == null ? new SpatialClusterer() : clusterer;
    }
}
