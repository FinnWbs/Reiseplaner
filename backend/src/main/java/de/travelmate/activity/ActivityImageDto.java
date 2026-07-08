package de.travelmate.activity;

public record ActivityImageDto(
    String url,
    String alt,
    String credit,
    String source
) {
    public static ActivityImageDto from(ActivityImageEntity image) {
        String url = image.url == null || image.url.isBlank()
            ? "/activities/" + image.activity.id + "/images/" + image.id + "/media"
            : image.url;
        return new ActivityImageDto(url, image.alt, image.credit, image.source.name());
    }
}
