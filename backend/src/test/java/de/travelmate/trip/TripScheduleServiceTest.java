package de.travelmate.trip;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TripScheduleServiceTest {
    @Test
    void temporaryPositionsStayPositiveDuringScheduleMove() {
        int firstTemporaryPosition = TripScheduleService.temporaryPositionFor(0);
        int secondTemporaryPosition = TripScheduleService.temporaryPositionFor(1);

        assertTrue(firstTemporaryPosition > 0);
        assertTrue(secondTemporaryPosition > firstTemporaryPosition);
    }
}
