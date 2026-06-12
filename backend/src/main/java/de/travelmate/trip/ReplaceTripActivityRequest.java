package de.travelmate.trip;

import jakarta.validation.constraints.NotNull;

public record ReplaceTripActivityRequest(@NotNull Long activityId, String notes, Boolean locked) {}
