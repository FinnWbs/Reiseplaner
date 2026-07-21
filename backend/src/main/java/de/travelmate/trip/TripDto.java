package de.travelmate.trip;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record TripDto(
    Long id,
    String city,
    String country,
    String countryCode,
    String state,
    Double latitude,
    Double longitude,
    String placeId,
    int daysCount,
    TripStatus status,
    LocalDate startDate,
    LocalDate endDate,
    String preferredMonth,
    TripPace pace,
    DayRhythm dayRhythm,
    DestinationSource destinationSource,
    List<String> selectedInterests,
    List<TripDayDto> days
) {
    public static TripDto from(TripEntity trip) {
        return new TripDto(
            trip.id,
            trip.city,
            trip.country,
            trip.countryCode,
            trip.state,
            trip.latitude,
            trip.longitude,
            trip.placeId,
            trip.daysCount,
            trip.status,
            trip.startDate,
            trip.endDate,
            trip.preferredMonth,
            trip.pace,
            trip.dayRhythm,
            trip.destinationSource,
            trip.selectedInterests.stream()
                .map(interest -> interest.code)
                .sorted(Comparator.naturalOrder())
                .toList(),
            trip.days.stream().map(TripDayDto::from).toList()
        );
    }
}
