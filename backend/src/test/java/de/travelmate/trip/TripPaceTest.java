package de.travelmate.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TripPaceTest {
    @Test
    void activityCountsMatchTripRhythmRanges() {
        assertEquals(2, TripPace.RELAXED.activitiesPerDay());
        assertEquals(3, TripPace.BALANCED.activitiesPerDay());
        assertEquals(4, TripPace.ACTIVE.activitiesPerDay());
    }
}
