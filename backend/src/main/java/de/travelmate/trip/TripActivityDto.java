package de.travelmate.trip;

import de.travelmate.activity.ActivityDto;
import de.travelmate.planning.ActivityTimeRules;

public record TripActivityDto(
    Long id,
    int position,
    boolean locked,
    String notes,
    int scheduledStart,
    int durationMinutes,
    boolean fitsAvailability,
    ActivityDto activity
) {
    public static TripActivityDto from(TripDayActivityEntity item) {
        boolean fits = item.scheduledStart >= item.tripDay.availableFrom
            && item.scheduledStart + item.durationMinutes <= item.tripDay.availableUntil
            && ActivityTimeRules.fitsAt(item.activity, item.scheduledStart, item.durationMinutes);
        return new TripActivityDto(
            item.id,
            item.position,
            item.locked,
            item.notes,
            item.scheduledStart,
            item.durationMinutes,
            fits,
            ActivityDto.from(item.activity)
        );
    }
}
