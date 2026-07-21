package de.travelmate.feedback;

public record FeedbackRequest(
    String pageUrl,
    String targetLabel,
    String targetSelector,
    String screenshotDataUrl,
    String description
) {}
