package de.travelmate.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateTripRequest(
    @NotBlank String city,
    @Min(1) @Max(14) int daysCount,
    List<Long> interestIds
) {}
