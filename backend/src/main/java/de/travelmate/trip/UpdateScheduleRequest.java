package de.travelmate.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateScheduleRequest(@NotEmpty List<@Valid ScheduleDayRequest> days) {}
