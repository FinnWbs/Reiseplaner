package de.travelmate.planning;

import de.travelmate.interest.InterestType;
import java.util.Map;
import java.util.Set;

public record SpatialCoverageReport(
    String city,
    InterestType interest,
    int totalCandidates,
    int clusterCount,
    Map<Integer, Integer> candidatesByCluster,
    double dominantClusterShare,
    Map<String, Integer> distanceBandCounts,
    int candidatesOutside3Km,
    int candidatesOutside7Km,
    boolean hasOuterCoverage,
    Set<SpatialCoverageWarningCode> warnings
) {
    public boolean insufficient() {
        return warnings.contains(SpatialCoverageWarningCode.IMPORT_SPATIAL_COVERAGE_INSUFFICIENT);
    }
}
