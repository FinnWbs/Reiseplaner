package de.travelmate.activity;

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
    double dataQualityScore
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
            activity.dataQualityScore
        );
    }
}
