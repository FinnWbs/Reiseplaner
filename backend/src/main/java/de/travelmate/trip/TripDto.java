package de.travelmate.trip;

import java.util.List;

public record TripDto(Long id, String city, int daysCount, TripStatus status, List<TripDayDto> days) {
    public static TripDto from(TripEntity trip) {
        return new TripDto(trip.id, trip.city, trip.daysCount, trip.status, trip.days.stream().map(TripDayDto::from).toList());
    }
}
