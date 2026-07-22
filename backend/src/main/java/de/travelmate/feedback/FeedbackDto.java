package de.travelmate.feedback;

import java.time.LocalDateTime;

public record FeedbackDto(
    Long id,
    String userEmail,
    String pageUrl,
    String targetLabel,
    String targetSelector,
    String screenshotDataUrl,
    String description,
    LocalDateTime createdAt
) {
    public static FeedbackDto from(FeedbackEntity entity) {
        return new FeedbackDto(
            entity.id,
            entity.user.email,
            entity.pageUrl,
            entity.targetLabel,
            entity.targetSelector,
            entity.screenshotDataUrl,
            entity.description,
            entity.createdAt
        );
    }
}
