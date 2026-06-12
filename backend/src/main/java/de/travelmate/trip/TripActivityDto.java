package de.travelmate.trip;

import de.travelmate.activity.ActivityDto;

public record TripActivityDto(Long id, int position, boolean locked, String notes, ActivityDto activity) {
    public static TripActivityDto from(TripDayActivityEntity item) {
        return new TripActivityDto(item.id, item.position, item.locked, item.notes, ActivityDto.from(item.activity));
    }
}
