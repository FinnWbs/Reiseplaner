package de.travelmate.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ScheduleDayRequest(
    @NotNull Long dayId,
    Integer dayNumber,
    List<Long> activityItemIds,
    List<@Valid ScheduleActivityRequest> activities
) {
    public List<Long> orderedItemIds() {
        if (activities != null && !activities.isEmpty()) {
            return activities.stream()
                .map(ScheduleActivityRequest::itemId)
                .toList();
        }
        return activityItemIds == null ? Collections.emptyList() : activityItemIds;
    }

    public Map<Long, ScheduleActivityRequest> activityDetailsByItemId() {
        if (activities == null || activities.isEmpty()) return Collections.emptyMap();
        return activities.stream()
            .collect(Collectors.toMap(
                ScheduleActivityRequest::itemId,
                Function.identity(),
                (first, ignored) -> first
            ));
    }
}
