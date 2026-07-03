package de.travelmate.planning;

import java.util.List;
import java.util.Map;

public record SpatialDiagnostics(
    Long tripId,
    String cityName,
    int totalActivities,
    int tripDays,
    int activitiesWithCoordinates,
    Double cityCenterLat,
    Double cityCenterLon,
    double averageDistanceFromCityCenterKm,
    double medianDistanceFromCityCenterKm,
    double maxDistanceFromCityCenterKm,
    Map<String, Integer> distanceBandCounts,
    int uniqueSpatialClusters,
    Map<Integer, Integer> activitiesPerCluster,
    Map<Integer, Integer> daysPerCluster,
    double dominantClusterShare,
    double averageIntraDayDistanceKm,
    double averageInterDayClusterDistanceKm,
    int repeatedClusterDays,
    double spatialDiversityScore,
    List<SpatialWarningCode> warnings
) {
    public boolean hasWarning(SpatialWarningCode warning) {
        return warnings.contains(warning);
    }
}
