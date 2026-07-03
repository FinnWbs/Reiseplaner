package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpatialClusterer {
    public List<SpatialCluster> clusterScored(
        List<ScoredActivity> scored,
        double radiusKm,
        Double cityCenterLat,
        Double cityCenterLon
    ) {
        List<ScoredActivity> withCoordinates = scored.stream()
            .filter(item -> hasCoordinates(item.activity()))
            .sorted(Comparator.comparingDouble(ScoredActivity::totalScore).reversed()
                .thenComparing(item -> item.activity().name == null ? "" : item.activity().name))
            .toList();
        List<MutableCluster> clusters = new ArrayList<>();
        for (ScoredActivity item : withCoordinates) {
            MutableCluster nearest = nearestWithinRadius(item.activity(), clusters, radiusKm);
            if (nearest == null) {
                nearest = new MutableCluster(clusters.size() + 1);
                clusters.add(nearest);
            }
            nearest.add(item.activity(), item.totalScore());
        }
        return immutableClusters(clusters, cityCenterLat, cityCenterLon);
    }

    public List<SpatialCluster> clusterActivities(
        List<ActivityEntity> activities,
        double radiusKm,
        Double cityCenterLat,
        Double cityCenterLon
    ) {
        List<ScoredActivity> scored = activities.stream()
            .map(activity -> new ScoredActivity(activity, activity.finalScore > 0 ? activity.finalScore : activity.dataQualityScore))
            .toList();
        return clusterScored(scored, radiusKm, cityCenterLat, cityCenterLon);
    }

    public Map<Long, Integer> clusterByActivityId(List<SpatialCluster> clusters) {
        return clusters.stream()
            .flatMap(cluster -> cluster.activityIds().stream().map(id -> Map.entry(id, cluster.id())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
    }

    public static double distanceKm(ActivityEntity first, ActivityEntity second) {
        if (!hasCoordinates(first) || !hasCoordinates(second)) {
            return Double.NaN;
        }
        return distanceKm(first.latitude, first.longitude, second.latitude, second.longitude);
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    static boolean hasCoordinates(ActivityEntity activity) {
        return activity != null && activity.latitude != null && activity.longitude != null;
    }

    private MutableCluster nearestWithinRadius(ActivityEntity activity, List<MutableCluster> clusters, double radiusKm) {
        MutableCluster nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (MutableCluster cluster : clusters) {
            double distance = distanceKm(activity.latitude, activity.longitude, cluster.centerLat(), cluster.centerLon());
            if (distance <= radiusKm && distance < nearestDistance) {
                nearest = cluster;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private List<SpatialCluster> immutableClusters(
        List<MutableCluster> clusters,
        Double cityCenterLat,
        Double cityCenterLon
    ) {
        return clusters.stream()
            .map(cluster -> cluster.toSpatialCluster(cityCenterLat, cityCenterLon))
            .toList();
    }

    private static final class MutableCluster {
        private final int id;
        private final List<Long> activityIds = new ArrayList<>();
        private final Set<String> interests = new HashSet<>();
        private double latSum;
        private double lonSum;
        private double scoreSum;
        private int count;

        private MutableCluster(int id) {
            this.id = id;
        }

        private void add(ActivityEntity activity, double score) {
            if (activity.id != null) {
                activityIds.add(activity.id);
            }
            if (activity.primaryInterest != null) {
                interests.add(activity.primaryInterest.name());
            }
            latSum += activity.latitude;
            lonSum += activity.longitude;
            scoreSum += score;
            count++;
        }

        private double centerLat() {
            return latSum / Math.max(1, count);
        }

        private double centerLon() {
            return lonSum / Math.max(1, count);
        }

        private SpatialCluster toSpatialCluster(Double cityCenterLat, Double cityCenterLon) {
            double distanceFromCenter = cityCenterLat == null || cityCenterLon == null
                ? 0
                : distanceKm(cityCenterLat, cityCenterLon, centerLat(), centerLon());
            return new SpatialCluster(
                id,
                centerLat(),
                centerLon(),
                List.copyOf(activityIds),
                count,
                scoreSum / Math.max(1, count),
                Set.copyOf(interests),
                distanceFromCenter
            );
        }
    }
}
