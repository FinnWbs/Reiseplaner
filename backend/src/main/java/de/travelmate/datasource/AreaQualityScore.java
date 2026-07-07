package de.travelmate.datasource;

public record AreaQualityScore(
    String areaId,
    double candidateQuality,
    double categoryCoverage,
    double reachability,
    double uniqueness,
    double spatialContribution,
    double score,
    boolean daySuitable
) {}
