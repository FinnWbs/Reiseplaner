package de.travelmate.trip;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TripDateValidatorTest {
    private final TripDateValidator validator = new TripDateValidator();

    @Test
    void datedTripRequiresAtLeastOnePlanningDate() {
        LocalDate start = LocalDate.of(2026, 7, 13);
        LocalDate end = LocalDate.of(2026, 7, 17);

        BadRequestException error = assertThrows(
            BadRequestException.class,
            () -> validator.validate(start, end, List.of())
        );

        assertEquals("Mindestens ein Planungstag ist erforderlich.", error.getMessage());
    }

    @Test
    void planningDatesAreSortedAndDeduplicated() {
        LocalDate start = LocalDate.of(2026, 7, 13);
        LocalDate end = LocalDate.of(2026, 7, 17);

        List<LocalDate> result = validator.validate(
            start,
            end,
            List.of(end, start, end)
        );

        assertEquals(List.of(start, end), result);
    }

    @Test
    void rejectsDatesOutsideTravelRange() {
        LocalDate start = LocalDate.of(2026, 7, 13);
        LocalDate end = LocalDate.of(2026, 7, 17);

        assertThrows(
            BadRequestException.class,
            () -> validator.validate(start, end, List.of(start.minusDays(1)))
        );
    }

    @Test
    void rejectsInvertedTravelRange() {
        LocalDate start = LocalDate.of(2026, 7, 17);
        LocalDate end = LocalDate.of(2026, 7, 13);

        assertThrows(
            BadRequestException.class,
            () -> validator.validate(start, end, List.of(start))
        );
    }

    @Test
    void rejectsMoreThanFourteenPlanningDates() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        List<LocalDate> dates = java.util.stream.IntStream.range(0, 15)
            .mapToObj(start::plusDays)
            .toList();

        assertThrows(BadRequestException.class, () -> validator.validate(start, end, dates));
    }

    @Test
    void undatedTripRemainsValidWithoutPlanningDates() {
        assertEquals(List.of(), validator.validate(null, null, List.of()));
    }
}
