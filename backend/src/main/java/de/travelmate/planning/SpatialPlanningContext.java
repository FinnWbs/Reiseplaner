package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.trip.TripDayEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SpatialPlanningContext {
    private final Map<Long, Integer> clusterByActivityId;
    private final Map<Integer, SpatialCluster> clusterById;
    private final Map<Integer, Integer> preferredClusterByDay;
    private final SpatialPlanningSettings settings;

    SpatialPlanningContext(
        Map<Long, Integer> clusterByActivityId,
        Map<Integer, SpatialCluster> clusterById,
        Map<Integer, Integer> preferredClusterByDay,
        SpatialPlanningSettings settings
    ) {
        this.clusterByActivityId = Map.copyOf(clusterByActivityId);
        this.clusterById = Map.copyOf(clusterById);
        this.preferredClusterByDay = Map.copyOf(preferredClusterByDay);
        this.settings = settings;
    }

    static SpatialPlanningContext empty(SpatialPlanningSettings settings) {
        return new SpatialPlanningContext(Map.of(), Map.of(), Map.of(), settings);
    }

    Optional<Integer> clusterId(ActivityEntity activity) {
        if (activity == null || activity.id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(clusterByActivityId.get(activity.id));
    }

    Optional<Integer> preferredCluster(TripDayEntity day) {
        return Optional.ofNullable(preferredClusterByDay.get(day.dayNumber));
    }

    boolean isPreferredCluster(TripDayEntity day, ActivityEntity activity) {
        Optional<Integer> cluster = clusterId(activity);
        Optional<Integer> preferred = preferredCluster(day);
        return cluster.isPresent() && preferred.isPresent() && cluster.get().equals(preferred.get());
    }

    boolean isNearPreferredCluster(TripDayEntity day, ActivityEntity activity) {
        Optional<Integer> cluster = clusterId(activity);
        Optional<Integer> preferred = preferredCluster(day);
        return cluster.isPresent()
            && preferred.isPresent()
            && !cluster.get().equals(preferred.get())
            && distanceBetweenClusters(cluster.get(), preferred.get()) <= settings.clusterRadiusKm() * 1.75;
    }

    boolean hasPreferredCluster(TripDayEntity day) {
        return preferredCluster(day).isPresent();
    }

    boolean isNonCenterCluster(ActivityEntity activity) {
        return clusterId(activity).filter(cluster -> !isCenterCluster(cluster)).isPresent();
    }

    boolean isSameOrNearDayCluster(TripDayEntity day, ActivityEntity activity) {
        Optional<Integer> cluster = clusterId(activity);
        if (cluster.isEmpty()) {
            return false;
        }
        for (var item : day.activities) {
            Optional<Integer> existing = clusterId(item.activity);
            if (existing.isEmpty()) {
                continue;
            }
            if (existing.get().equals(cluster.get())
                || distanceBetweenClusters(existing.get(), cluster.get()) <= settings.clusterRadiusKm() * 1.75) {
                return true;
            }
        }
        return false;
    }

    boolean isFarFromDayCluster(TripDayEntity day, ActivityEntity activity) {
        Optional<Integer> cluster = clusterId(activity);
        if (cluster.isEmpty() || day.activities.isEmpty()) {
            return false;
        }
        for (var item : day.activities) {
            Optional<Integer> existing = clusterId(item.activity);
            if (existing.isPresent()
                && distanceBetweenClusters(existing.get(), cluster.get()) <= settings.clusterRadiusKm() * 2.5) {
                return false;
            }
        }
        return true;
    }

    boolean wasClusterUsedBeforeDay(ActivityEntity activity, List<TripDayEntity> days, int dayNumber) {
        Optional<Integer> cluster = clusterId(activity);
        if (cluster.isEmpty()) {
            return false;
        }
        return days.stream()
            .filter(day -> day.dayNumber < dayNumber)
            .flatMap(day -> day.activities.stream())
            .anyMatch(item -> clusterId(item.activity).filter(cluster.get()::equals).isPresent());
    }

    int centerClusterUseBeforeDay(List<TripDayEntity> days, int dayNumber) {
        return (int) days.stream()
            .filter(day -> day.dayNumber < dayNumber)
            .flatMap(day -> day.activities.stream())
            .filter(item -> clusterId(item.activity).filter(this::isCenterCluster).isPresent())
            .count();
    }

    boolean isCenterCluster(ActivityEntity activity) {
        return clusterId(activity).filter(this::isCenterCluster).isPresent();
    }

    int preferredClusterUseBeforeDay(List<TripDayEntity> days, int dayNumber) {
        Optional<Integer> preferred = preferredClusterByDay.entrySet().stream()
            .filter(entry -> entry.getKey() == dayNumber)
            .map(Map.Entry::getValue)
            .findFirst();
        if (preferred.isEmpty()) {
            return 0;
        }
        return (int) days.stream()
            .filter(day -> day.dayNumber < dayNumber)
            .flatMap(day -> day.activities.stream())
            .filter(item -> clusterId(item.activity).filter(preferred.get()::equals).isPresent())
            .count();
    }

    int uniqueClusters() {
        return clusterById.size();
    }

    private boolean isCenterCluster(int clusterId) {
        SpatialCluster cluster = clusterById.get(clusterId);
        return cluster != null && cluster.distanceFromCityCenterKm() <= settings.centerDominanceRadiusKm();
    }

    private double distanceBetweenClusters(int first, int second) {
        SpatialCluster firstCluster = clusterById.get(first);
        SpatialCluster secondCluster = clusterById.get(second);
        if (firstCluster == null || secondCluster == null) {
            return Double.MAX_VALUE;
        }
        return SpatialClusterer.distanceKm(
            firstCluster.centerLat(),
            firstCluster.centerLon(),
            secondCluster.centerLat(),
            secondCluster.centerLon()
        );
    }
}
