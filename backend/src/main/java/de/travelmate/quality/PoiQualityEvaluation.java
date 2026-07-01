package de.travelmate.quality;

import java.util.Set;

public record PoiQualityEvaluation(
    CanonicalCategory canonicalCategory,
    double popularityScore,
    double notabilityScore,
    double qualityScore,
    double categoryFitScore,
    double itineraryFitScore,
    double diversityScore,
    double finalScore,
    double penalties,
    boolean hardExcluded,
    String hardExclusionReason,
    Set<QualityReasonCode> reasonCodes
) {}
