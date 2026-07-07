package de.travelmate.datasource;

public record ImportArea(
    String id,
    String label,
    double centerLat,
    double centerLon,
    int radiusMeters,
    double budgetShare,
    int rawTarget,
    ImportAreaType areaType,
    double distanceFromCityCenterKm,
    boolean reachable
) {}
