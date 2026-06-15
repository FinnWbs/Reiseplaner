package de.travelmate.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public record CreateTripRequest(
    @NotBlank String city,
    @Min(1) @Max(14) int daysCount,
    List<Long> interestIds,
    LocalDate startDate,
    LocalDate endDate,
    List<LocalDate> planningDates,
    TripPace pace,
    DayRhythm dayRhythm,
    DestinationSource destinationSource,
    String country,
    String countryCode,
    String state,
    Double latitude,
    Double longitude,
    String placeId
) {}
