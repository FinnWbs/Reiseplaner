package de.travelmate.trip;

import java.util.List;

public record TripDayDto(Long id, int dayNumber, List<TripActivityDto> activities) {
    public static TripDayDto from(TripDayEntity day) {
        return new TripDayDto(day.id, day.dayNumber, day.activities.stream().map(TripActivityDto::from).toList());
    }
}
