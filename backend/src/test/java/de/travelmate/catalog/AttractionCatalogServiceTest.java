package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityExternalRefEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.activity.ActivitySource;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripDayActivityEntity;
import de.travelmate.trip.TripDayActivityRepository;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import de.travelmate.trip.TripPace;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttractionCatalogServiceTest {
    @Test
    void berlinSeedIsNoLongerBundled() {
        List<AttractionCatalogEntry> entries = new AttractionCatalogSeedRepository().findByCity("Berlin");

        assertTrue(entries.isEmpty());
    }

    @Test
    void unsupportedCityReturnsEmptyCatalog() {
        AttractionCatalogResponse response = service().listForTrip(trip("Athen"));

        assertFalse(response.supported());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void generatedCatalogIsUsedWhenCityHasNoSeed() {
        AttractionCatalogService service = service(
            new CapturingActivityRepository(),
            new CapturingTripDayActivityRepository(),
            new FakeGeneratedCatalog(List.of(generatedEntry("athen-akropolis", "Akropolis", "Q131013", 92.4)))
        );

        AttractionCatalogResponse response = service.listForTrip(trip("Athen"));

        assertTrue(response.supported());
        assertEquals(1, response.items().size());
        assertEquals("Akropolis", response.items().get(0).name());
        assertEquals(92.4, response.items().get(0).publicAttractionScore());
        assertEquals("WIKIMEDIA", response.items().get(0).source());
    }

    @Test
    void marksAlreadyPlannedCatalogAttractionByWikidataId() {
        AttractionCatalogService service = service();
        TripEntity trip = trip("Berlin");
        TripDayEntity day = trip.days.get(0);
        ActivityEntity activity = activity("Brandenburger Tor", ActivitySource.WIKIDATA, "Q82425");
        addScheduled(day, activity);

        AttractionCatalogResponse response = service.listForTrip(trip);

        AttractionCatalogItemDto gate = response.items().stream()
            .filter(item -> "berlin-q82425".equals(item.catalogId()))
            .findFirst()
            .orElseThrow();
        assertTrue(gate.alreadyPlanned());
        assertEquals(List.of(1), gate.plannedDayNumbers());
    }

    @Test
    void addCatalogAttractionCreatesInactiveLockedTripItem() {
        CapturingActivityRepository activities = new CapturingActivityRepository();
        CapturingTripDayActivityRepository tripActivities = new CapturingTripDayActivityRepository();
        AttractionCatalogService service = service(activities, tripActivities);
        TripEntity trip = trip("Berlin");
        TripDayEntity day = trip.days.get(0);

        service.addToDay(trip, day.id, "berlin-q82425");

        assertEquals(1, day.activities.size());
        assertEquals(1, tripActivities.persisted.size());
        TripDayActivityEntity item = day.activities.get(0);
        assertTrue(item.locked);
        assertEquals("Brandenburger Tor", item.activity.name);
        assertEquals(ActivitySource.WIKIDATA, item.activity.source);
        assertEquals("Q82425", item.activity.externalId);
        assertFalse(item.activity.active);
        assertEquals(0, item.activity.importVersion);
        assertNotNull(item.scheduledStart);
    }

    @Test
    void duplicateCatalogAttractionIsRejected() {
        AttractionCatalogService service = service();
        TripEntity trip = trip("Berlin");
        addScheduled(trip.days.get(0), activity("Brandenburger Tor", ActivitySource.WIKIDATA, "Q82425"));

        assertThrows(
            BadRequestException.class,
            () -> service.addToDay(trip, trip.days.get(0).id, "berlin-q82425")
        );
    }

    @Test
    void catalogAttractionCanBeAppendedToFullDay() {
        AttractionCatalogService service = service();
        TripEntity trip = trip("Berlin");
        trip.pace = TripPace.RELAXED;
        TripDayEntity day = trip.days.get(0);
        addScheduled(day, activity("Existing A", ActivitySource.DEMO, "demo-a"));
        addScheduled(day, activity("Existing B", ActivitySource.DEMO, "demo-b"));

        service.addToDay(trip, day.id, "berlin-q151356");

        assertEquals(3, day.activities.size());
        assertEquals("Fernsehturm", day.activities.get(2).activity.name);
    }

    private static AttractionCatalogService service() {
        return service(new CapturingActivityRepository(), new CapturingTripDayActivityRepository());
    }

    private static AttractionCatalogService service(
        CapturingActivityRepository activities,
        CapturingTripDayActivityRepository tripActivities
    ) {
        return service(activities, tripActivities, new CityAwareGeneratedCatalog(
            "Berlin",
            List.of(
                generatedEntry("Berlin", "berlin-q82425", "Brandenburger Tor", "Q82425", 99.0),
                generatedEntry("Berlin", "berlin-q151356", "Fernsehturm", "Q151356", 94.0)
            )
        ));
    }

    private static AttractionCatalogService service(
        CapturingActivityRepository activities,
        CapturingTripDayActivityRepository tripActivities,
        AttractionCatalogGenerationService generatedCatalog
    ) {
        AttractionCatalogService service = new AttractionCatalogService();
        service.seeds = new AttractionCatalogSeedRepository();
        service.generatedCatalog = generatedCatalog;
        service.activities = activities;
        service.tripActivities = tripActivities;
        return service;
    }

    private static TripEntity trip(String city) {
        TripEntity trip = new TripEntity();
        trip.id = 99L;
        trip.city = city;
        trip.pace = TripPace.ACTIVE;
        trip.daysCount = 2;
        for (int index = 1; index <= 2; index++) {
            TripDayEntity day = new TripDayEntity();
            day.id = (long) index;
            day.dayNumber = index;
            day.trip = trip;
            trip.days.add(day);
        }
        return trip;
    }

    private static ActivityEntity activity(String name, ActivitySource source, String externalId) {
        ActivityEntity activity = new ActivityEntity();
        activity.id = Math.abs(externalId.hashCode()) + 1L;
        activity.name = name;
        activity.city = "Berlin";
        activity.source = source;
        activity.externalId = externalId;
        activity.active = true;
        activity.importVersion = 8;
        ActivityExternalRefEntity ref = new ActivityExternalRefEntity();
        ref.activity = activity;
        ref.source = source;
        ref.externalId = externalId;
        activity.externalRefs.add(ref);
        return activity;
    }

    private static void addScheduled(TripDayEntity day, ActivityEntity activity) {
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.id = activity.id + 10_000;
        item.tripDay = day;
        item.activity = activity;
        item.position = day.activities.size() + 1;
        day.activities.add(item);
    }

    static class CapturingActivityRepository extends ActivityRepository {
        final List<ActivityEntity> persisted = new ArrayList<>();

        @Override
        public Optional<ActivityEntity> findBySourceAndExternalId(ActivitySource source, String externalId) {
            return persisted.stream()
                .filter(activity -> activity.source == source && externalId.equals(activity.externalId))
                .findFirst();
        }

        @Override
        public Optional<ActivityEntity> findByExternalReference(ActivitySource source, String externalId) {
            return persisted.stream()
                .filter(activity -> activity.externalRefs.stream().anyMatch(ref ->
                    ref.source == source && externalId.equals(ref.externalId)
                ))
                .findFirst();
        }

        @Override
        public Optional<ActivityEntity> findByNormalizedNameAndCity(String name, String city) {
            return persisted.stream()
                .filter(activity -> activity.name.toLowerCase().equals(name)
                    && activity.city.toLowerCase().equals(city))
                .findFirst();
        }

        @Override
        public void persist(ActivityEntity entity) {
            entity.id = (long) persisted.size() + 1;
            persisted.add(entity);
        }
    }

    static class CapturingTripDayActivityRepository extends TripDayActivityRepository {
        final List<TripDayActivityEntity> persisted = new ArrayList<>();

        @Override
        public void persist(TripDayActivityEntity entity) {
            entity.id = (long) persisted.size() + 1;
            persisted.add(entity);
        }
    }

    static class FakeGeneratedCatalog extends AttractionCatalogGenerationService {
        private final List<AttractionCatalogEntry> entries;

        FakeGeneratedCatalog(List<AttractionCatalogEntry> entries) {
            this.entries = entries;
        }

        @Override
        public List<AttractionCatalogEntry> findOrGenerate(TripEntity trip) {
            return entries;
        }
    }

    static class CityAwareGeneratedCatalog extends AttractionCatalogGenerationService {
        private final String city;
        private final List<AttractionCatalogEntry> entries;

        CityAwareGeneratedCatalog(String city, List<AttractionCatalogEntry> entries) {
            this.city = city;
            this.entries = entries;
        }

        @Override
        public List<AttractionCatalogEntry> findOrGenerate(TripEntity trip) {
            return city.equals(trip.city) ? entries : List.of();
        }
    }

    private static AttractionCatalogEntry generatedEntry(
        String catalogId,
        String name,
        String wikidataId,
        double score
    ) {
        return generatedEntry("Athen", catalogId, name, wikidataId, score);
    }

    private static AttractionCatalogEntry generatedEntry(
        String city,
        String catalogId,
        String name,
        String wikidataId,
        double score
    ) {
        return new AttractionCatalogEntry(
            catalogId,
            name,
            city,
            wikidataId,
            "de.wikipedia.org",
            name,
            InterestType.SIGHTSEEING,
            "landmark",
            37.9715,
            23.7257,
            1,
            "Generiertes Highlight",
            score,
            123_456L,
            80,
            "WIKIMEDIA"
        );
    }
}
