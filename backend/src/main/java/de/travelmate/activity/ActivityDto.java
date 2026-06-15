package de.travelmate.activity;

import java.time.LocalDateTime;

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
            activity.lastSyncedAt,
            activity.createdAt,
            activity.updatedAt
        );
    }
}
