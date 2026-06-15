package de.travelmate.trip;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public record TripDayDto(
    Long id,
    int dayNumber,
    LocalDate travelDate,
    String weekday,
    int availableFrom,
    int availableUntil,
    List<TripActivityDto> activities
) {
    public static TripDayDto from(TripDayEntity day) {
        String weekday = day.travelDate == null
            ? null
            : day.travelDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN);
        return new TripDayDto(
            day.id,
            day.dayNumber,
            day.travelDate,
            weekday,
            day.availableFrom,
            day.availableUntil,
            day.activities.stream().map(TripActivityDto::from).toList()
        );
    }
}
