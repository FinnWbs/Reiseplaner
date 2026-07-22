package de.travelmate.planning;

import java.util.List;
import java.util.Set;

public record SpatialCluster(
    int id,
    double centerLat,
    double centerLon,
    List<Long> activityIds,
    int activityCount,
    double averageScore,
    Set<String> interests,
    double distanceFromCityCenterKm
) {}
