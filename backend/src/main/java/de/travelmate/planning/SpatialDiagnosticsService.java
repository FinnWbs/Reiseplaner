package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpatialDiagnosticsService {
    private static final List<String> DISTANCE_BANDS = List.of("0-1 km", "1-3 km", "3-7 km", "7-15 km", "15+ km");

    @Inject
    SpatialPlanningSettings settings;

    @Inject
    SpatialClusterer clusterer;

    public SpatialDiagnostics analyze(TripEntity trip) {
        List<ScheduledActivity> scheduled = trip.days.stream()
            .sorted(Comparator.comparingInt(day -> day.dayNumber))
            .flatMap(day -> day.activities.stream()
                .sorted(Comparator.comparingInt(item -> item.position))
                .map(item -> new ScheduledActivity(item.activity, day.dayNumber, item.position)))
            .toList();
        return analyze(trip.id, trip.city, trip.daysCount > 0 ? trip.daysCount : trip.days.size(),
            trip.latitude, trip.longitude, scheduled);
    }

    SpatialDiagnostics analyze(
        Long tripId,
        String city,
        int tripDays,
        Double cityCenterLat,
        Double cityCenterLon,
        List<ScheduledActivity> scheduled
    ) {
        List<ScheduledActivity> withCoordinates = scheduled.stream()
            .filter(item -> SpatialClusterer.hasCoordinates(item.activity()))
            .toList();
        Center center = resolveCenter(cityCenterLat, cityCenterLon, withCoordinates);
        List<ActivityEntity> coordinateActivities = withCoordinates.stream().map(ScheduledActivity::activity).toList();
        List<SpatialCluster> clusters = clusterer().clusterActivities(
            coordinateActivities,
            settings().clusterRadiusKm(),
            center.lat(),
            center.lon()
        );
        Map<Long, Integer> clusterByActivity = clusterer().clusterByActivityId(clusters);
        Map<Integer, Integer> activitiesPerCluster = activitiesPerCluster(clusters);
        Map<Integer, Integer> daysPerCluster = daysPerCluster(withCoordinates, clusterByActivity);
        List<Double> distancesFromCenter = distancesFromCenter(withCoordinates, center);
        Map<String, Integer> distanceBandCounts = distanceBandCounts(distancesFromCenter);
        double dominantClusterShare = dominantClusterShare(activitiesPerCluster, withCoordinates.size());
        int repeatedClusterDays = repeatedClusterDays(withCoordinates, clusterByActivity);
        double averageIntraDayDistance = averageIntraDayDistance(withCoordinates);
        double averageInterDayClusterDistance = averageInterDayClusterDistance(withCoordinates, clusterByActivity, clusters);
        double averageDistance = average(distancesFromCenter);
        double medianDistance = median(distancesFromCenter);
        double maxDistance = distancesFromCenter.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double diversityScore = diversityScore(
            clusters.size(),
            Math.max(1, tripDays),
            dominantClusterShare,
            averageDistance,
            repeatedClusterDays
        );
        List<SpatialWarningCode> warnings = warnings(
            scheduled.size(),
            withCoordinates.size(),
            Math.max(1, tripDays),
            clusters.size(),
            dominantClusterShare,
            averageDistance,
            medianDistance,
            repeatedClusterDays,
            diversityScore
        );
        return new SpatialDiagnostics(
            tripId,
            city,
            scheduled.size(),
            Math.max(1, tripDays),
            withCoordinates.size(),
            center.lat(),
            center.lon(),
            averageDistance,
            medianDistance,
            maxDistance,
            distanceBandCounts,
            clusters.size(),
            activitiesPerCluster,
            daysPerCluster,
            dominantClusterShare,
            averageIntraDayDistance,
            averageInterDayClusterDistance,
            repeatedClusterDays,
            diversityScore,
            warnings
        );
    }

    private Center resolveCenter(Double cityCenterLat, Double cityCenterLon, List<ScheduledActivity> activities) {
        if (cityCenterLat != null && cityCenterLon != null) {
            return new Center(cityCenterLat, cityCenterLon);
        }
        OptionalDouble lat = activities.stream().map(ScheduledActivity::activity)
            .mapToDouble(activity -> activity.latitude).average();
        OptionalDouble lon = activities.stream().map(ScheduledActivity::activity)
            .mapToDouble(activity -> activity.longitude).average();
        return new Center(lat.isPresent() ? lat.getAsDouble() : null, lon.isPresent() ? lon.getAsDouble() : null);
    }

    private Map<Integer, Integer> activitiesPerCluster(List<SpatialCluster> clusters) {
        return clusters.stream().collect(Collectors.toMap(
            SpatialCluster::id,
            SpatialCluster::activityCount,
            Integer::sum,
            LinkedHashMap::new
        ));
    }

    private Map<Integer, Integer> daysPerCluster(
        List<ScheduledActivity> scheduled,
        Map<Long, Integer> clusterByActivity
    ) {
        Map<Integer, Set<Integer>> days = new HashMap<>();
        for (ScheduledActivity item : scheduled) {
            Integer cluster = clusterByActivity.get(item.activity().id);
            if (cluster != null) {
                days.computeIfAbsent(cluster, ignored -> new java.util.HashSet<>()).add(item.dayNumber());
            }
        }
        return days.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue().size(),
            Integer::sum,
            LinkedHashMap::new
        ));
    }

    private List<Double> distancesFromCenter(List<ScheduledActivity> scheduled, Center center) {
        if (center.lat() == null || center.lon() == null) {
            return List.of();
        }
        return scheduled.stream()
            .map(ScheduledActivity::activity)
            .map(activity -> SpatialClusterer.distanceKm(center.lat(), center.lon(), activity.latitude, activity.longitude))
            .sorted()
            .toList();
    }

    private Map<String, Integer> distanceBandCounts(List<Double> distances) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        DISTANCE_BANDS.forEach(band -> counts.put(band, 0));
        for (double distance : distances) {
            counts.merge(distanceBand(distance), 1, Integer::sum);
        }
        return counts;
    }

    private String distanceBand(double distance) {
        if (distance < 1) return "0-1 km";
        if (distance < 3) return "1-3 km";
        if (distance < 7) return "3-7 km";
        if (distance < 15) return "7-15 km";
        return "15+ km";
    }

    private double dominantClusterShare(Map<Integer, Integer> activitiesPerCluster, int activitiesWithCoordinates) {
        if (activitiesWithCoordinates == 0) {
            return 0;
        }
        int dominantCount = activitiesPerCluster.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return dominantCount / (double) activitiesWithCoordinates;
    }

    private int repeatedClusterDays(List<ScheduledActivity> scheduled, Map<Long, Integer> clusterByActivity) {
        Map<Integer, Integer> dominantByDay = dominantClusterByDay(scheduled, clusterByActivity);
        int repeated = 0;
        Integer previous = null;
        for (Integer day : dominantByDay.keySet().stream().sorted().toList()) {
            Integer current = dominantByDay.get(day);
            if (current != null && current.equals(previous)) {
                repeated++;
            }
            previous = current;
        }
        return repeated;
    }

    private double averageIntraDayDistance(List<ScheduledActivity> scheduled) {
        Map<Integer, List<ScheduledActivity>> byDay = scheduled.stream()
            .collect(Collectors.groupingBy(ScheduledActivity::dayNumber));
        List<Double> distances = new ArrayList<>();
        for (List<ScheduledActivity> dayItems : byDay.values()) {
            List<ScheduledActivity> ordered = dayItems.stream()
                .sorted(Comparator.comparingInt(ScheduledActivity::position))
                .toList();
            for (int index = 1; index < ordered.size(); index++) {
                double distance = SpatialClusterer.distanceKm(ordered.get(index - 1).activity(), ordered.get(index).activity());
                if (!Double.isNaN(distance)) {
                    distances.add(distance);
                }
            }
        }
        return average(distances);
    }

    private double averageInterDayClusterDistance(
        List<ScheduledActivity> scheduled,
        Map<Long, Integer> clusterByActivity,
        List<SpatialCluster> clusters
    ) {
        Map<Integer, Integer> dominantByDay = dominantClusterByDay(scheduled, clusterByActivity);
        Map<Integer, SpatialCluster> clusterById = clusters.stream()
            .collect(Collectors.toMap(SpatialCluster::id, cluster -> cluster));
        List<Integer> days = dominantByDay.keySet().stream().sorted().toList();
        List<Double> distances = new ArrayList<>();
        for (int index = 1; index < days.size(); index++) {
            SpatialCluster previous = clusterById.get(dominantByDay.get(days.get(index - 1)));
            SpatialCluster current = clusterById.get(dominantByDay.get(days.get(index)));
            if (previous != null && current != null) {
                distances.add(SpatialClusterer.distanceKm(
                    previous.centerLat(),
                    previous.centerLon(),
                    current.centerLat(),
                    current.centerLon()
                ));
            }
        }
        return average(distances);
    }

    private Map<Integer, Integer> dominantClusterByDay(
        List<ScheduledActivity> scheduled,
        Map<Long, Integer> clusterByActivity
    ) {
        Map<Integer, Map<Integer, Integer>> counts = new LinkedHashMap<>();
        for (ScheduledActivity item : scheduled) {
            Integer cluster = clusterByActivity.get(item.activity().id);
            if (cluster != null) {
                counts.computeIfAbsent(item.dayNumber(), ignored -> new HashMap<>())
                    .merge(cluster, 1, Integer::sum);
            }
        }
        Map<Integer, Integer> dominant = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : counts.entrySet()) {
            entry.getValue().entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .ifPresent(cluster -> dominant.put(entry.getKey(), cluster.getKey()));
        }
        return dominant;
    }

    private double diversityScore(
        int clusters,
        int tripDays,
        double dominantClusterShare,
        double averageDistance,
        int repeatedClusterDays
    ) {
        int expectedClusters = tripDays >= settings().longTripDayThreshold()
            ? settings().minExpectedClustersForLongTrip()
            : Math.min(2, tripDays);
        double clusterScore = Math.min(1, clusters / (double) Math.max(1, expectedClusters));
        double dominanceScore = 1 - Math.max(0, dominantClusterShare - settings().maxDominantClusterShare())
            / Math.max(0.01, 1 - settings().maxDominantClusterShare());
        double distanceScore = Math.min(1, averageDistance / settings().centerDominanceRadiusKm());
        double repeatedScore = tripDays <= 1 ? 1 : 1 - Math.min(1, repeatedClusterDays / (double) (tripDays - 1));
        return clamp01(clusterScore * 0.35 + dominanceScore * 0.30 + distanceScore * 0.20 + repeatedScore * 0.15);
    }

    private List<SpatialWarningCode> warnings(
        int totalActivities,
        int activitiesWithCoordinates,
        int tripDays,
        int clusters,
        double dominantClusterShare,
        double averageDistance,
        double medianDistance,
        int repeatedClusterDays,
        double diversityScore
    ) {
        EnumSet<SpatialWarningCode> warnings = EnumSet.noneOf(SpatialWarningCode.class);
        if (totalActivities > 0 && activitiesWithCoordinates < Math.max(2, totalActivities / 2)) {
            warnings.add(SpatialWarningCode.INSUFFICIENT_COORDINATES_FOR_SPATIAL_DIAGNOSTICS);
        }
        if (dominantClusterShare > settings().maxDominantClusterShare()) {
            warnings.add(SpatialWarningCode.TOO_MANY_ACTIVITIES_IN_ONE_CLUSTER);
        }
        if (activitiesWithCoordinates > 0
            && dominantClusterShare > settings().maxDominantClusterShare()
            && medianDistance <= settings().centerDominanceRadiusKm()) {
            warnings.add(SpatialWarningCode.DOMINANT_CENTER_CLUSTER);
        }
        if (activitiesWithCoordinates > 0 && averageDistance <= settings().centerDominanceRadiusKm()) {
            warnings.add(SpatialWarningCode.LOW_DISTANCE_FROM_CITY_CENTER);
        }
        if (repeatedClusterDays >= Math.max(2, tripDays / 3)) {
            warnings.add(SpatialWarningCode.REPEATED_CLUSTER_ACROSS_DAYS);
        }
        if (diversityScore < 0.55) {
            warnings.add(SpatialWarningCode.SPATIAL_DIVERSITY_LOW);
        }
        if (tripDays >= settings().longTripDayThreshold()
            && activitiesWithCoordinates >= settings().minExpectedClustersForLongTrip()
            && clusters < settings().minExpectedClustersForLongTrip()) {
            warnings.add(SpatialWarningCode.MULTI_AREA_IMPORT_RECOMMENDED);
        }
        return List.copyOf(warnings);
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double median(List<Double> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2;
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private SpatialPlanningSettings settings() {
        return settings == null ? new SpatialPlanningSettings() : settings;
    }

    private SpatialClusterer clusterer() {
        return clusterer == null ? new SpatialClusterer() : clusterer;
    }

    record ScheduledActivity(ActivityEntity activity, int dayNumber, int position) {}

    private record Center(Double lat, Double lon) {}
}
