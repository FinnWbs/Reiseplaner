package de.travelmate.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class AttractionCatalogScorer {
    private static final double PAGEVIEW_WEIGHT = 0.45;
    private static final double SITELINK_WEIGHT = 0.25;
    private static final double CATEGORY_WEIGHT = 0.20;
    private static final double DATA_QUALITY_WEIGHT = 0.10;
    private static final double SITELINK_REFERENCE = Math.log10(101);

    public List<WikimediaCatalogCandidate> scoreAndRank(List<WikimediaCatalogCandidate> candidates, int maxItems) {
        long maxPageviews = candidates.stream()
            .mapToLong(candidate -> candidate.pageviews)
            .max()
            .orElse(0);
        double pageviewReference = Math.max(1.0, Math.log10(maxPageviews + 1.0));

        candidates.forEach(candidate -> {
            double pageviewScore = Math.log10(candidate.pageviews + 1.0) / pageviewReference;
            double sitelinkScore = Math.min(1.0, Math.log10(candidate.sitelinkCount + 1.0) / SITELINK_REFERENCE);
            candidate.dataQualityScore = dataQualityScore(candidate);
            candidate.publicAttractionScore = clamp01(
                PAGEVIEW_WEIGHT * pageviewScore
                    + SITELINK_WEIGHT * sitelinkScore
                    + CATEGORY_WEIGHT * candidate.categoryFitScore
                    + DATA_QUALITY_WEIGHT * candidate.dataQualityScore
            ) * 100.0;
        });

        return candidates.stream()
            .sorted(Comparator
                .comparingDouble((WikimediaCatalogCandidate candidate) -> candidate.publicAttractionScore).reversed()
                .thenComparing(Comparator.comparingLong((WikimediaCatalogCandidate candidate) -> candidate.pageviews).reversed())
                .thenComparing(Comparator.comparingInt((WikimediaCatalogCandidate candidate) -> candidate.sitelinkCount).reversed())
                .thenComparing(candidate -> candidate.name))
            .limit(maxItems)
            .toList();
    }

    private static double dataQualityScore(WikimediaCatalogCandidate candidate) {
        double score = 0.0;
        if (candidate.hasCoordinates) score += 0.30;
        if (candidate.hasImage) score += 0.25;
        if (candidate.description != null && !candidate.description.isBlank()) score += 0.20;
        if (candidate.hasWebsite) score += 0.15;
        if (candidate.wikipediaTitle != null && !candidate.wikipediaTitle.isBlank()) score += 0.10;
        return clamp01(score);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
