package de.travelmate.activity;

import java.time.LocalDateTime;
import de.travelmate.quality.CanonicalCategory;

public record ActivityDto(
    Long id,
    String externalId,
    ActivitySource source,
    String name,
    String description,
    String city,
    String category,
    String subcategory,
    Double latitude,
    Double longitude,
    String address,
    Double rating,
    double dataQualityScore,
    CanonicalCategory canonicalCategory,
    double popularityScore,
    double notabilityScore,
    double qualityScore,
    double categoryFitScore,
    double itineraryFitScore,
    double finalScore,
    String qualityReasonCodes,
    LocalDateTime lastSyncedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ActivityDto from(ActivityEntity activity) {
        return new ActivityDto(
            activity.id,
            activity.externalId,
            activity.source,
            activity.name,
            activity.description,
            activity.city,
            activity.category,
            activity.subcategory,
            activity.latitude,
            activity.longitude,
            activity.address,
            activity.rating,
            activity.dataQualityScore,
            activity.canonicalCategory,
            activity.popularityScore,
            activity.notabilityScore,
            activity.qualityScore,
            activity.categoryFitScore,
            activity.itineraryFitScore,
            activity.finalScore,
            activity.qualityReasonCodes,
            activity.lastSyncedAt,
            activity.createdAt,
            activity.updatedAt
        );
    }
}
