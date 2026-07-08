package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.trip.TripDayActivityEntity;
import de.travelmate.trip.TripDayEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.Optional;

@ApplicationScoped
public class DayScheduler {
    static final int SLOT_GAP_MINUTES = 30;

    public void scheduleDay(TripDayEntity day, boolean lockItems) {
        day.activities.sort(Comparator.comparingInt(item -> item.position));
        int cursor = day.availableFrom;
        for (int index = 0; index < day.activities.size(); index++) {
            TripDayActivityEntity item = day.activities.get(index);
            ActivityTimeRules.TimeProfile profile = ActivityTimeRules.profile(item.activity);
            int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
            if (start < profile.preferredStart() && profile.preferredStart() + item.durationMinutes <= day.availableUntil) {
                start = profile.preferredStart();
            }
            start = Math.min(start, 1440 - item.durationMinutes);
            item.position = index + 1;
            item.scheduledStart = start;
            if (lockItems) {
                item.locked = true;
            }
            cursor = start + item.durationMinutes + SLOT_GAP_MINUTES;
        }
    }

    Optional<PlanSlotChoice> slotFor(TripDayEntity day, ActivityEntity activity) {
        ActivityTimeRules.TimeProfile profile = ActivityTimeRules.profile(activity);
        int cursor = day.activities.stream()
            .mapToInt(item -> item.scheduledStart + item.durationMinutes + SLOT_GAP_MINUTES)
            .max()
            .orElse(day.availableFrom);
        int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
        if (start < profile.preferredStart()
            && profile.preferredStart() >= day.availableFrom
            && profile.preferredStart() + profile.durationMinutes() <= day.availableUntil) {
            start = profile.preferredStart();
        }
        int latestEnd = Math.min(day.availableUntil, profile.latestEnd());
        return start + profile.durationMinutes() <= latestEnd
            ? Optional.of(new PlanSlotChoice(activity, start, profile.durationMinutes()))
            : Optional.empty();
    }

    void renumber(TripDayEntity day) {
        day.activities.sort(Comparator.comparingInt(item -> item.position));
        for (int i = 0; i < day.activities.size(); i++) {
            day.activities.get(i).position = i + 1;
        }
    }
}
