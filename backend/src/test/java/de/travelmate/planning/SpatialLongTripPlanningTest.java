package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityInterestEntity;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.sync.ActivitySyncService;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import de.travelmate.trip.TripPace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialLongTripPlanningTest {
    @Test
    void berlinSevenDayFixtureUsesSeveralClusters() {
        Fixture fixture = fixture("Berlin", 52.5200, 13.4050, List.of(
            new ClusterSpec("Mitte", 52.5200, 13.4050, 0.84),
            new ClusterSpec("Charlottenburg", 52.5048, 13.3050, 0.82),
            new ClusterSpec("Kreuzberg", 52.4986, 13.4030, 0.82),
            new ClusterSpec("Prenzlauer Berg", 52.5386, 13.4243, 0.82)
        ));

        PlanningService service = serviceWithActivities(fixture.activities());
        service.generatePlan(fixture.trip(), fixture.interestIds(), fixture.types());

        SpatialDiagnostics diagnostics = service.lastSpatialDiagnostics().orElseThrow();
        assertFullyPlanned(fixture.trip());
        assertTrue(diagnostics.uniqueSpatialClusters() >= 3);
        assertTrue(diagnostics.dominantClusterShare() <= 0.70);
    }

    @Test
    void tokyoSevenDayFixtureUsesSeveralClustersAndKeepsNightlifeLate() {
        Fixture fixture = fixture("Tokyo", 35.6812, 139.7671, List.of(
            new ClusterSpec("Tokyo Station", 35.6812, 139.7671, 0.84),
            new ClusterSpec("Shinjuku", 35.6896, 139.7006, 0.82),
            new ClusterSpec("Ueno", 35.7148, 139.7745, 0.82),
            new ClusterSpec("Odaiba", 35.6266, 139.7755, 0.82)
        ));
        fixture.activities().add(invalidOldNatureViewpoint(9_999L, fixture.interests().get(InterestType.NATURE)));

        PlanningService service = serviceWithActivities(fixture.activities());
        service.generatePlan(fixture.trip(), fixture.interestIds(), fixture.types());

        SpatialDiagnostics diagnostics = service.lastSpatialDiagnostics().orElseThrow();
        assertFullyPlanned(fixture.trip());
        assertTrue(diagnostics.uniqueSpatialClusters() >= 3);
        assertTrue(fixture.trip().days.stream()
            .flatMap(day -> day.activities.stream())
            .noneMatch(item -> item.activity.name.contains("Viewpoint 6F")));
        assertTrue(fixture.trip().days.stream()
            .flatMap(day -> day.activities.stream())
            .filter(item -> item.activity.primaryInterest == InterestType.NIGHTLIFE)
            .allMatch(item -> item.scheduledStart >= 1080));
    }

    @Test
    void shanghaiSevenDayFixtureAvoidsCenterOnlyPlan() {
        Fixture fixture = fixture("Shanghai", 31.2304, 121.4737, List.of(
            new ClusterSpec("People Square", 31.2304, 121.4737, 0.84),
            new ClusterSpec("Pudong", 31.2397, 121.4998, 0.82),
            new ClusterSpec("French Concession", 31.2133, 121.4452, 0.82),
            new ClusterSpec("Century Park", 31.2142, 121.5504, 0.82)
        ));

        PlanningService service = serviceWithActivities(fixture.activities());
        service.generatePlan(fixture.trip(), fixture.interestIds(), fixture.types());

        SpatialDiagnostics diagnostics = service.lastSpatialDiagnostics().orElseThrow();
        assertFullyPlanned(fixture.trip());
        assertTrue(diagnostics.uniqueSpatialClusters() >= 3);
        assertTrue(diagnostics.dominantClusterShare() <= 0.70);
    }

    private static Fixture fixture(String city, double centerLat, double centerLon, List<ClusterSpec> clusters) {
        Map<InterestType, InterestEntity> interests = interests();
        List<ActivityEntity> activities = new ArrayList<>();
        long id = 1;
        for (ClusterSpec cluster : clusters) {
            for (InterestType type : InterestType.primaryTypes()) {
                activities.add(activity(
                    id++,
                    city + " " + cluster.name() + " " + type.name(),
                    city,
                    type,
                    interests.get(type),
                    cluster.lat() + (id % 3) * 0.0008,
                    cluster.lon() + (id % 4) * 0.0008,
                    cluster.score()
                ));
            }
        }
        ClusterSpec center = clusters.get(0);
        for (InterestType type : InterestType.primaryTypes()) {
            activities.add(activity(
                id++,
                city + " Central Extra " + type.name(),
                city,
                type,
                interests.get(type),
                center.lat() + (id % 5) * 0.0005,
                center.lon() + (id % 6) * 0.0005,
                0.85
            ));
        }

        TripEntity trip = new TripEntity();
        trip.city = city;
        trip.latitude = centerLat;
        trip.longitude = centerLon;
        trip.daysCount = 7;
        trip.pace = TripPace.BALANCED;
        trip.selectedInterests = new HashSet<>(interests.values());
        for (int dayNumber = 1; dayNumber <= 7; dayNumber++) {
            TripDayEntity day = new TripDayEntity();
            day.id = (long) dayNumber;
            day.dayNumber = dayNumber;
            day.trip = trip;
            day.availableFrom = 540;
            day.availableUntil = 1440;
            trip.days.add(day);
        }
        return new Fixture(trip, activities, interests);
    }

    private static PlanningService serviceWithActivities(List<ActivityEntity> activities) {
        PlanningService service = new PlanningService();
        service.activities = new ActivityRepository() {
            @Override
            public List<ActivityEntity> findActiveByCity(String city) {
                return activities.stream().filter(activity -> activity.city.equals(city)).toList();
            }
        };
        service.sync = new ActivitySyncService();
        service.timeRules = new ActivityTimeRules();
        return service;
    }

    private static Map<InterestType, InterestEntity> interests() {
        Map<InterestType, InterestEntity> interests = new java.util.EnumMap<>(InterestType.class);
        long id = 1;
        for (InterestType type : InterestType.primaryTypes()) {
            InterestEntity interest = new InterestEntity();
            interest.id = id++;
            interest.name = type.displayName();
            interest.code = type.name();
            interests.put(type, interest);
        }
        return interests;
    }

    private static ActivityEntity activity(
        long id,
        String name,
        String city,
        InterestType type,
        InterestEntity interest,
        double lat,
        double lon,
        double score
    ) {
        ActivityEntity activity = new ActivityEntity();
        activity.id = id;
        activity.name = name;
        activity.city = city;
        activity.primaryInterest = type;
        activity.category = type.displayName();
        activity.subcategory = type.name().toLowerCase(java.util.Locale.ROOT);
        activity.latitude = lat;
        activity.longitude = lon;
        activity.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION;
        activity.finalScore = score;
        activity.categoryFitScore = 1;
        activity.itineraryFitScore = 1;
        activity.dataQualityScore = score;
        activity.interestScores.add(mapping(activity, interest, 10));
        return activity;
    }

    private static ActivityEntity invalidOldNatureViewpoint(long id, InterestEntity nature) {
        ActivityEntity activity = activity(
            id,
            "Tokyo Station train tracks Viewpoint 6F",
            "Tokyo",
            InterestType.NATURE,
            nature,
            35.6812,
            139.7671,
            0.99
        );
        activity.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION - 1;
        return activity;
    }

    private static ActivityInterestEntity mapping(ActivityEntity activity, InterestEntity interest, int score) {
        ActivityInterestEntity mapping = new ActivityInterestEntity();
        mapping.activity = activity;
        mapping.interest = interest;
        mapping.score = score;
        return mapping;
    }

    private static void assertFullyPlanned(TripEntity trip) {
        assertEquals(21, trip.days.stream().mapToInt(day -> day.activities.size()).sum());
        assertTrue(trip.days.stream().allMatch(day -> day.activities.size() == 3));
    }

    private record ClusterSpec(String name, double lat, double lon, double score) {}

    private record Fixture(
        TripEntity trip,
        List<ActivityEntity> activities,
        Map<InterestType, InterestEntity> interests
    ) {
        List<Long> interestIds() {
            return interests.values().stream().map(interest -> interest.id).toList();
        }

        Set<InterestType> types() {
            return interests.keySet();
        }
    }
}
