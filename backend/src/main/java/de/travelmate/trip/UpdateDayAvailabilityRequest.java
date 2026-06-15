package de.travelmate.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateDayAvailabilityRequest(
    @Min(0) @Max(1410) int availableFrom,
    @Min(30) @Max(1440) int availableUntil
) {}
