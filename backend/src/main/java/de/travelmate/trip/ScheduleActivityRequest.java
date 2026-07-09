package de.travelmate.trip;

import jakarta.validation.constraints.NotNull;

public record ScheduleActivityRequest(
    @NotNull Long itemId,
    Integer scheduledStart,
    Integer durationMinutes
) {}
