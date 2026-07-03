package de.travelmate.activity;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import java.util.Map;
import java.util.Set;

public record ImportDemand(
    String city,
    Set<InterestType> selectedInterests,
    int tripDays,
    TripPace pace,
    int activitiesPerDay,
    int targetActivities,
    int eligiblePoolTarget,
    int rawPoolTargetTotal,
    Map<InterestType, Integer> rawTargetByInterest,
    Map<InterestType, Integer> eligibleTargetByInterest
) {
    public int rawTargetFor(InterestType interest) {
        return rawTargetByInterest.getOrDefault(interest, 0);
    }

    public int eligibleTargetFor(InterestType interest) {
        return eligibleTargetByInterest.getOrDefault(interest, 0);
    }
}
