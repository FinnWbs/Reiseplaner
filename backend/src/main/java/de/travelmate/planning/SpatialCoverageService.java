package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SpatialCoverageService {
    private static final List<String> DISTANCE_BANDS = List.of("0-1 km", "1-3 km", "3-7 km", "7-15 km", "15+ km");

    @Inject
    SpatialClusterer clusterer;

    @Inject
    SpatialPlanningSettings settings;

    public SpatialCoverageReport analyze(
        String city,
        InterestType interest,
        List<ActivityEntity> activities,
        ImportDemand demand,
        Double centerLat,
        Double centerLon
    ) {
        List<ActivityEntity> relevant = activities.stream()
            .filter(activity -> interest == null || activity.primaryInterest == interest)
            .filter(SpatialClusterer::hasCoordinates)
            .toList();
        Center center = center(centerLat, centerLon, relevant);
        List<SpatialCluster> clusters = clusterer().clusterActivities(
            relevant,
            settings().clusterRadiusKm(),
            center.lat(),
            center.lon()
        );
        Map<Integer, Integer> byCluster = clusters.stream()
            .sorted(Comparator.comparingInt(SpatialCluster::id))
            .collect(Collectors.toMap(
                SpatialCluster::id,
                SpatialCluster::activityCount,
                Integer::sum,
                LinkedHashMap::new
            ));
        List<Double> distances = relevant.stream()
            .map(activity -> SpatialClusterer.distanceKm(center.lat(), center.lon(), activity.latitude, activity.longitude))
            .toList();
        Map<String, Integer> bands = distanceBandCounts(distances);
        int outside3 = (int) distances.stream().filter(distance -> distance >= 3).count();
        int outside7 = (int) distances.stream().filter(distance -> distance >= 7).count();
        double dominantShare = dominantShare(byCluster, relevant.size());
        EnumSet<SpatialCoverageWarningCode> warnings = EnumSet.noneOf(SpatialCoverageWarningCode.class);
        boolean longTrip = demand != null && demand.requireOuterCoverageForLongTrip();
        int minClusters = demand == null ? 1 : demand.minSpatialClusters();
        double maxDominant = demand == null ? 0.70 : demand.maxDominantClusterShare();
        int minOuter = demand == null ? 0 : demand.minOuterDistanceBandCandidates();

        if (longTrip && outside3 < minOuter) {
            warnings.add(SpatialCoverageWarningCode.INTEREST_ONLY_HAS_CENTER_CANDIDATES);
        }
        if (longTrip && outside3 == 0 && relevant.size() > 0) {
            warnings.add(SpatialCoverageWarningCode.CENTER_BIAS_DOMINATES_IMPORT);
        }
        if (clusters.size() < minClusters || dominantShare > maxDominant || (longTrip && outside3 < minOuter)) {
            warnings.add(SpatialCoverageWarningCode.IMPORT_SPATIAL_COVERAGE_INSUFFICIENT);
            warnings.add(SpatialCoverageWarningCode.MULTI_AREA_IMPORT_RECOMMENDED);
        }
        if (longTrip && outside3 < minOuter) {
            warnings.add(SpatialCoverageWarningCode.IMPORT_RADIUS_TOO_SMALL_FOR_LONG_TRIP);
        }

        return new SpatialCoverageReport(
            city,
            interest,
            relevant.size(),
            clusters.size(),
            byCluster,
            dominantShare,
            bands,
            outside3,
            outside7,
            outside3 >= Math.max(1, minOuter),
            Set.copyOf(warnings)
        );
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

    private double dominantShare(Map<Integer, Integer> byCluster, int total) {
        if (total == 0) {
            return 0;
        }
        return byCluster.values().stream().mapToInt(Integer::intValue).max().orElse(0) / (double) total;
    }

    private Center center(Double lat, Double lon, List<ActivityEntity> activities) {
        if (lat != null && lon != null) {
            return new Center(lat, lon);
        }
        double averageLat = activities.stream().mapToDouble(activity -> activity.latitude).average().orElse(0);
        double averageLon = activities.stream().mapToDouble(activity -> activity.longitude).average().orElse(0);
        return new Center(averageLat, averageLon);
    }

    private SpatialClusterer clusterer() {
        return clusterer == null ? new SpatialClusterer() : clusterer;
    }

    private SpatialPlanningSettings settings() {
        return settings == null ? new SpatialPlanningSettings() : settings;
    }

    private record Center(double lat, double lon) {}
}
