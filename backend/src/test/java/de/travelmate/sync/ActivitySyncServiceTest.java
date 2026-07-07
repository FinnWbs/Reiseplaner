package de.travelmate.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityImportService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import de.travelmate.planning.SpatialCoverageService;
import de.travelmate.trip.TripPace;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivitySyncServiceTest {
    @Test
    void badCoverageForTwoDayTripAlreadyRecommendsMultiArea() {
        ActivitySyncService service = service(false);

        RefreshDecision decision = service.refreshDecision(
            "Berlin",
            Set.of(InterestType.CULTURE),
            demand(false, 2),
            52.52,
            13.405
        );

        assertEquals(RefreshDecision.MULTI_AREA_RECOMMENDED_ONLY, decision);
    }

    @Test
    void badCoverageWithoutMultiAreaOnlyRecommends() {
        ActivitySyncService service = service(false);

        RefreshDecision decision = service.refreshDecision(
            "Berlin",
            Set.of(InterestType.CULTURE),
            demand(false, 7),
            52.52,
            13.405
        );

        assertEquals(RefreshDecision.MULTI_AREA_RECOMMENDED_ONLY, decision);
    }

    @Test
    void badCoverageWithMultiAreaRequiresRefresh() {
        ActivitySyncService service = service(false);

        RefreshDecision decision = service.refreshDecision(
            "Berlin",
            Set.of(InterestType.CULTURE),
            demand(true, 7),
            52.52,
            13.405
        );

        assertEquals(RefreshDecision.MULTI_AREA_REFRESH_REQUIRED, decision);
    }

    private static ActivitySyncService service(boolean importCalled) {
        ActivitySyncService service = new ActivitySyncService();
        service.activities = new ActivityRepository() {
            @Override
            public List<ActivityEntity> findActiveByCity(String city) {
                return java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> activity(100L + index, "Center " + index, 52.52 + index * 0.0003, 13.405))
                    .toList();
            }

            @Override
            public long countActiveByCityAndInterest(String city, InterestType interest) {
                return 20;
            }
        };
        service.states = new ActivityImportStateRepository() {
            @Override
            public Optional<ActivityImportStateEntity> findForCityAndInterest(String city, InterestType interest) {
                ActivityImportStateEntity state = new ActivityImportStateEntity();
                state.city = city;
                state.interest = interest;
                state.importVersion = de.travelmate.activity.ActivityPersistenceService.CURRENT_IMPORT_VERSION;
                state.syncedAt = LocalDateTime.now();
                return Optional.of(state);
            }
        };
        service.coverage = new SpatialCoverageService();
        service.importer = new ActivityImportService();
        return service;
    }

    private static ImportDemand demand(boolean multiAreaAllowed, int days) {
        return new ImportDemand(
            "Berlin",
            Set.of(InterestType.CULTURE),
            days,
            TripPace.BALANCED,
            3,
            days * 3,
            53,
            120,
            Map.of(InterestType.CULTURE, 120),
            Map.of(InterestType.CULTURE, 18),
            days <= 2 ? 2 : 3,
            days <= 2 ? 0.60 : 0.55,
            days <= 2 ? 0 : 4,
            true,
            multiAreaAllowed
        );
    }

    private static ActivityEntity activity(long id, String name, double lat, double lon) {
        ActivityEntity activity = new ActivityEntity();
        activity.id = id;
        activity.name = name;
        activity.city = "Berlin";
        activity.latitude = lat;
        activity.longitude = lon;
        activity.primaryInterest = InterestType.CULTURE;
        activity.finalScore = 0.8;
        activity.dataQualityScore = 0.8;
        activity.importVersion = de.travelmate.activity.ActivityPersistenceService.CURRENT_IMPORT_VERSION;
        return activity;
    }
}
