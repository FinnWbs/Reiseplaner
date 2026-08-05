package de.travelmate.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestType;
import de.travelmate.planning.ActivityTimeRules;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TripScheduleServiceTest {
    @Test
    void temporaryPositionsStayPositiveDuringScheduleMove() {
        int firstTemporaryPosition = TripScheduleService.temporaryPositionFor(0);
        int secondTemporaryPosition = TripScheduleService.temporaryPositionFor(1);

        assertTrue(firstTemporaryPosition > 0);
        assertTrue(secondTemporaryPosition > firstTemporaryPosition);
    }

    @Test
    void scheduleDayRequestKeepsLegacyItemOrder() {
        ScheduleDayRequest request = new ScheduleDayRequest(1L, null, List.of(11L, 12L), null);

        assertEquals(List.of(11L, 12L), request.orderedItemIds());
        assertTrue(request.activityDetailsByItemId().isEmpty());
    }

    @Test
    void scheduleDayRequestUsesDetailedItemsWhenPresent() {
        ScheduleDayRequest request = new ScheduleDayRequest(
            1L,
            null,
            List.of(99L),
            List.of(
                new ScheduleActivityRequest(11L, 720, 90),
                new ScheduleActivityRequest(12L, 780, 120)
            )
        );

        assertEquals(List.of(11L, 12L), request.orderedItemIds());
        assertEquals(720, request.activityDetailsByItemId().get(11L).scheduledStart());
        assertEquals(120, request.activityDetailsByItemId().get(12L).durationMinutes());
    }

    @Test
    void addNightlifeActivityExtendsDayUntilMidnightAndAppendsAfterExistingStop() {
        ActivityEntity bar = new ActivityEntity();
        bar.id = 99L;
        bar.name = "Bar";
        bar.active = true;
        bar.primaryInterest = InterestType.NIGHTLIFE;
        TripScheduleService service = serviceWithActivity(bar);
        TripEntity trip = new TripEntity();
        trip.pace = TripPace.ACTIVE;
        TripDayEntity day = new TripDayEntity();
        day.id = 7L;
        day.trip = trip;
        day.availableFrom = 540;
        day.availableUntil = 1200;
        TripDayActivityEntity existing = new TripDayActivityEntity();
        existing.id = 11L;
        existing.tripDay = day;
        existing.activity = new ActivityEntity();
        existing.position = 1;
        existing.scheduledStart = 1080;
        existing.durationMinutes = 90;
        day.activities.add(existing);
        trip.days.add(day);

        service.addActivity(trip, 7L, new ReplaceTripActivityRequest(99L, null, true));

        assertEquals(1440, day.availableUntil);
        assertEquals(2, day.activities.size());
        assertEquals(1200, day.activities.get(1).scheduledStart);
        assertEquals(180, day.activities.get(1).durationMinutes);
    }

    @Test
    void deleteActivityRemovesItemThroughDayCollectionAndRenumbersRemainingItems() {
        TripScheduleService service = new TripScheduleService();
        service.tripActivities = new TripDayActivityRepository() {
            @Override
            public void flush() {
                // No persistence context is needed for this collection-level regression test.
            }
        };
        TripEntity trip = new TripEntity();
        TripDayEntity day = new TripDayEntity();
        day.id = 7L;
        day.trip = trip;
        trip.days.add(day);

        for (int position = 1; position <= 4; position++) {
            TripDayActivityEntity item = new TripDayActivityEntity();
            item.id = 10L + position;
            item.tripDay = day;
            item.position = position;
            day.activities.add(item);
        }

        service.deleteActivity(trip, 7L, 12L);

        assertEquals(List.of(11L, 13L, 14L), day.activities.stream().map(item -> item.id).toList());
        assertEquals(List.of(1, 2, 3), day.activities.stream().map(item -> item.position).toList());
    }

    private static TripScheduleService serviceWithActivity(ActivityEntity activity) {
        TripScheduleService service = new TripScheduleService();
        service.timeRules = new ActivityTimeRules();
        service.activities = new ActivityRepository() {
            @Override
            public Optional<ActivityEntity> findByIdOptional(Long id) {
                return Optional.of(activity);
            }
        };
        return service;
    }
}
