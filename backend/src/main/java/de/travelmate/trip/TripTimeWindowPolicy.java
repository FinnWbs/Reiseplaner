package de.travelmate.trip;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class TripTimeWindowPolicy {
    public static final int NIGHTLIFE_AVAILABLE_UNTIL = 1440;

    public void extendDaysForInterests(TripEntity trip, Set<InterestType> selectedTypes) {
        if (trip == null || selectedTypes == null || !selectedTypes.contains(InterestType.NIGHTLIFE)) {
            return;
        }
        trip.days.forEach(this::extendDayForNightlife);
    }

    public void extendDayForActivity(TripDayEntity day, ActivityEntity activity) {
        if (activity != null && activity.primaryInterest == InterestType.NIGHTLIFE) {
            extendDayForNightlife(day);
        }
    }

    private void extendDayForNightlife(TripDayEntity day) {
        if (day != null && day.availableUntil < NIGHTLIFE_AVAILABLE_UNTIL) {
            day.availableUntil = NIGHTLIFE_AVAILABLE_UNTIL;
        }
    }
}
