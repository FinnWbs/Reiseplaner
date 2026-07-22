package de.travelmate.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import de.travelmate.interest.InterestType;

public record CreateTripRequest(
    @NotBlank String city,
    @Min(1) @Max(14) int daysCount,
    List<Long> interestIds,
    List<InterestType> interests,
    LocalDate startDate,
    LocalDate endDate,
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String preferredMonth,
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
