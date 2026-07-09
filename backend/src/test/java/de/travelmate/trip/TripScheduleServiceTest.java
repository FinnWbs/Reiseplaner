package de.travelmate.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
        ScheduleDayRequest request = new ScheduleDayRequest(1L, List.of(11L, 12L), null);

        assertEquals(List.of(11L, 12L), request.orderedItemIds());
        assertTrue(request.activityDetailsByItemId().isEmpty());
    }

    @Test
    void scheduleDayRequestUsesDetailedItemsWhenPresent() {
        ScheduleDayRequest request = new ScheduleDayRequest(
            1L,
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
}
