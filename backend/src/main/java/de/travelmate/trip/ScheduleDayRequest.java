package de.travelmate.trip;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleDayRequest(@NotNull Long dayId, @NotNull List<Long> activityItemIds) {}
