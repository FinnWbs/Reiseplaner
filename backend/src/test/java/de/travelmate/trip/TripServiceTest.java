package de.travelmate.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestRepository;
import de.travelmate.interest.InterestType;
import de.travelmate.planning.PlanningService;
import de.travelmate.user.CurrentUserService;
import de.travelmate.user.UserEntity;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TripServiceTest {
    @Test
    void createUsesInterestIdsFromFrontendContract() {
        InterestEntity culture = interest(1L, InterestType.CULTURE);
        InterestEntity nature = interest(3L, InterestType.NATURE);
        TestPlanningService planning = new TestPlanningService();
        CapturingTripRepository trips = new CapturingTripRepository();
        TripService service = service(planning, trips, culture, nature);

        TripDto created = service.create(request(List.of(1L, 3L), null));

        assertEquals(99L, created.id());
        assertEquals(Set.of(culture, nature), trips.persisted.selectedInterests);
        assertEquals(List.of(1L, 3L), planning.interestIds);
        assertEquals(Set.of(InterestType.CULTURE, InterestType.NATURE), planning.selectedTypes);
    }

    @Test
    void createKeepsInterestTypeCompatibility() {
        InterestEntity food = interest(4L, InterestType.FOOD);
        TestPlanningService planning = new TestPlanningService();
        CapturingTripRepository trips = new CapturingTripRepository();
        TripService service = service(planning, trips, food);

        service.create(request(List.of(), List.of(InterestType.FOOD)));

        assertEquals(Set.of(food), trips.persisted.selectedInterests);
        assertEquals(List.of(4L), planning.interestIds);
        assertEquals(Set.of(InterestType.FOOD), planning.selectedTypes);
    }

    @Test
    void createRejectsMissingInterestsInsteadOfPersistingEmptyPlan() {
        CapturingTripRepository trips = new CapturingTripRepository();
        TripService service = service(new TestPlanningService(), trips);

        assertThrows(BadRequestException.class, () -> service.create(request(List.of(), null)));
        assertTrue(trips.persisted == null);
    }

    private static TripService service(
        TestPlanningService planning,
        CapturingTripRepository trips,
        InterestEntity... availableInterests
    ) {
        TripService service = new TripService();
        service.currentUser = new TestCurrentUserService();
        service.trips = trips;
        service.planning = planning;
        service.dateValidator = new TripDateValidator();
        service.interests = new TestInterestRepository(List.of(availableInterests));
        return service;
    }

    private static CreateTripRequest request(List<Long> interestIds, List<InterestType> interestTypes) {
        return new CreateTripRequest(
            "Berlin",
            2,
            interestIds,
            interestTypes,
            null,
            null,
            List.of(),
            TripPace.BALANCED,
            DayRhythm.BALANCED,
            DestinationSource.KNOWN,
            "Deutschland",
            "de",
            null,
            52.5173885,
            13.3951309,
            "berlin-place"
        );
    }

    private static InterestEntity interest(Long id, InterestType type) {
        InterestEntity interest = new InterestEntity();
        interest.id = id;
        interest.code = type.name();
        interest.name = type.displayName();
        return interest;
    }

    static class TestCurrentUserService extends CurrentUserService {
        @Override
        public UserEntity requireCurrentUser() {
            UserEntity user = new UserEntity();
            user.id = 7L;
            user.email = "test@example.com";
            return user;
        }
    }

    static class CapturingTripRepository extends TripRepository {
        TripEntity persisted;

        @Override
        public void persist(TripEntity entity) {
            entity.id = 99L;
            persisted = entity;
        }
    }

    static class TestPlanningService extends PlanningService {
        List<Long> interestIds = List.of();
        Set<InterestType> selectedTypes = Set.of();

        @Override
        public void generatePlan(TripEntity trip, List<Long> interestIds, Set<InterestType> requestedInterests) {
            this.interestIds = List.copyOf(interestIds);
            this.selectedTypes = Set.copyOf(requestedInterests);
            trip.status = TripStatus.PLANNED;
        }
    }

    static class TestInterestRepository extends InterestRepository {
        private final List<InterestEntity> interests;

        TestInterestRepository(List<InterestEntity> interests) {
            this.interests = interests;
        }

        @Override
        public List<InterestEntity> findByIds(List<Long> ids) {
            return interests.stream().filter(interest -> ids.contains(interest.id)).toList();
        }

        @Override
        public List<InterestEntity> findByCodes(Set<InterestType> codes) {
            return interests.stream()
                .filter(interest -> codes.stream().map(Enum::name).anyMatch(interest.code::equals))
                .toList();
        }
    }
}
