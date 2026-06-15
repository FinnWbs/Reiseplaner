package de.travelmate.trip;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record UpdateTripDatesRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull List<LocalDate> planningDates
) {}
