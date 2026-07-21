package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityInterestEntity;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.sync.ActivitySyncService;
import de.travelmate.sync.RefreshDecision;
import de.travelmate.trip.TripDayActivityEntity;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PlanningServiceTest {
    @Test
    void scheduleDayUsesDurationAndGapForSequentialStartTimes() {
        PlanningService service = serviceWithActivities();
        TripDayEntity day = emptyDay();
        TripDayActivityEntity museum = scheduledItem(activity(1L, "Museum", 5.0, 1.0));
        TripDayActivityEntity gallery = scheduledItem(activity(2L, "Gallery", 4.0, 1.0));
        museum.tripDay = day;
        gallery.tripDay = day;
        museum.position = 1;
        gallery.position = 2;
        museum.durationMinutes = 120;
        gallery.durationMinutes = 90;
        day.activities.add(museum);
        day.activities.add(gallery);

        service.scheduleDay(day, true);

        assertEquals(600, museum.scheduledStart);
        assertEquals(750, gallery.scheduledStart);
        assertNotEquals(museum.scheduledStart, gallery.scheduledStart);
        assertTrue(museum.locked);
        assertTrue(gallery.locked);
    }

    @Test
    void scheduleDayPrefersEveningForNightlifeWhenAvailable() {
        PlanningService service = serviceWithActivities();
        TripDayEntity day = emptyDay();
        day.availableFrom = 720;
        day.availableUntil = 1440;
        TripDayActivityEntity club = scheduledItem(activity(1L, "Club", 5.0, 1.0));
        club.activity.category = "Nightlife";
        club.activity.subcategory = "club";
        club.tripDay = day;
        club.durationMinutes = 180;
        day.activities.add(club);

        service.scheduleDay(day, false);

        assertEquals(1200, club.scheduledStart);
    }

    @Test
    void scheduleDayPrefersMiddayForFoodWhenAvailable() {
        PlanningService service = serviceWithActivities();
        TripDayEntity day = emptyDay();
        TripDayActivityEntity restaurant = scheduledItem(activity(1L, "Restaurant", 5.0, 1.0));
        restaurant.activity.category = "Food";
        restaurant.activity.subcategory = "restaurant";
        restaurant.tripDay = day;
        restaurant.durationMinutes = 90;
        day.activities.add(restaurant);

        service.scheduleDay(day, false);

        assertEquals(720, restaurant.scheduledStart);
    }

    @Test
    void replacementNeverReturnsTheActivityBeingReplaced() {
        ActivityEntity current = activity(1L, "Museum", 5.0, 1.0);
        ActivityEntity alternative = activity(2L, "Gallery", 4.0, 1.0);
        PlanningService service = serviceWithActivities(current, alternative);
        TripDayActivityEntity item = scheduledItem(current);

        Optional<ActivityEntity> replacement = service.replacementFor(item.tripDay.trip, item, Set.of());

        assertEquals(alternative.id, replacement.orElseThrow().id);
        assertEquals(1, item.position);
        assertEquals(600, item.scheduledStart);
        assertEquals(90, item.durationMinutes);
    }

    @Test
    void replacementExcludesActivitiesUsedInOtherSlots() {
        ActivityEntity current = activity(1L, "Museum", 5.0, 1.0);
        ActivityEntity usedElsewhere = activity(2L, "Gallery", 4.8, 1.0);
        ActivityEntity freeAlternative = activity(3L, "Monument", 4.0, 1.0);
        PlanningService service = serviceWithActivities(current, usedElsewhere, freeAlternative);
        TripDayActivityEntity item = scheduledItem(current);
        TripDayActivityEntity otherItem = scheduledItem(usedElsewhere);
        otherItem.tripDay = item.tripDay;
        otherItem.position = 2;
        item.tripDay.activities.add(otherItem);

        Optional<ActivityEntity> replacement = service.replacementFor(item.tripDay.trip, item, Set.of());

        assertEquals(freeAlternative.id, replacement.orElseThrow().id);
    }

    @Test
    void replacementCanBeRestrictedToPreferredInterest() {
        InterestEntity food = interest(4L, "Essen & Cafes");
        food.code = InterestType.FOOD.name();
        InterestEntity shopping = interest(5L, "Shopping");
        shopping.code = InterestType.SHOPPING.name();
        ActivityEntity current = primaryActivity(1L, "Old Stop", InterestType.SHOPPING, shopping);
        ActivityEntity shoppingAlternative = primaryActivity(2L, "Mall", InterestType.SHOPPING, shopping);
        ActivityEntity foodAlternative = primaryActivity(3L, "Cafe", InterestType.FOOD, food);
        PlanningService service = serviceWithActivities(current, shoppingAlternative, foodAlternative);
        TripDayActivityEntity item = scheduledItem(current);
        item.scheduledStart = 720;

        Optional<ActivityEntity> replacement = service.replacementFor(
            item.tripDay.trip,
            item,
            Set.of(),
            InterestType.FOOD
        );

        assertEquals(foodAlternative.id, replacement.orElseThrow().id);
    }

    @Test
    void scoreUsesInterestMatchAsDominantFactor() {
        PlanningService service = new PlanningService();
        InterestEntity culture = interest(1L, "Kultur");

        ActivityEntity strongMatch = activity(4.0, 0.7);
        strongMatch.interestScores.add(mapping(strongMatch, culture, 10));

        ActivityEntity weakNoMatch = activity(5.0, 1.0);

        double matched = service.score(strongMatch, Set.of(1L)).totalScore();
        double unmatched = service.score(weakNoMatch, Set.of(1L)).totalScore();

        assertTrue(matched > unmatched);
    }

    @Test
    void activityWithoutSelectedInterestCanScoreZero() {
        PlanningService service = new PlanningService();
        ActivityEntity activity = activity(null, 0);

        assertEquals(0, service.score(activity, Set.of(99L)).totalScore());
    }

    @Test
    void oldImportVersionScoresZeroForNewPlans() {
        PlanningService service = new PlanningService();
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();
        ActivityEntity oldNature = primaryActivity(5L, "Old Urban Viewpoint", InterestType.NATURE, nature);
        oldNature.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION - 1;

        assertEquals(0, service.score(oldNature, Set.of(3L)).totalScore());
    }

    @Test
    void generatesBalancedPlanAcrossSelectedInterests() {
        InterestEntity culture = interest(1L, "Kultur & Museen");
        culture.code = InterestType.CULTURE.name();
        InterestEntity food = interest(2L, "Essen & Cafés");
        food.code = InterestType.FOOD.name();
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();

        ActivityEntity museum = primaryActivity(1L, "Museum", InterestType.CULTURE, culture);
        ActivityEntity gallery = primaryActivity(2L, "Galerie", InterestType.CULTURE, culture);
        ActivityEntity restaurant = primaryActivity(3L, "Restaurant", InterestType.FOOD, food);
        ActivityEntity park = primaryActivity(4L, "Stadtpark", InterestType.NATURE, nature);
        PlanningService service = serviceWithActivities(museum, gallery, restaurant, park);
        service.sync = new ActivitySyncService() {
            @Override
            public boolean needsRefresh(String city, Set<InterestType> interests) {
                return false;
            }
        };
        TripDayEntity day = emptyDay();
        day.trip.pace = de.travelmate.trip.TripPace.ACTIVE;
        day.trip.selectedInterests = new HashSet<>(Set.of(culture, food, nature));

        service.generatePlan(day.trip, List.of(1L, 2L, 3L), Set.of(
            InterestType.CULTURE, InterestType.FOOD, InterestType.NATURE
        ));

        Set<InterestType> scheduled = day.activities.stream()
            .map(item -> item.activity.primaryInterest)
            .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(InterestType.CULTURE, InterestType.FOOD, InterestType.NATURE), scheduled);
    }

    @Test
    void generatePlanDoesNotUseOldNatureCandidatesAsFallback() {
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();
        ActivityEntity oldViewpoint = primaryActivity(5L, "Tokyo Station train tracks Viewpoint 6F", InterestType.NATURE, nature);
        oldViewpoint.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION - 1;

        PlanningService service = serviceWithActivities(oldViewpoint);
        service.sync = noRefreshSync();
        TripDayEntity day = emptyDay();
        day.trip.selectedInterests = new HashSet<>(Set.of(nature));

        service.generatePlan(day.trip, List.of(3L), Set.of(InterestType.NATURE));

        assertTrue(day.activities.isEmpty());
    }

    @Test
    void generatePlanDoesNotUseInactiveCatalogActivityAsAutomaticCandidate() {
        InterestEntity sightseeing = interest(6L, "Sightseeing");
        sightseeing.code = InterestType.SIGHTSEEING.name();
        ActivityEntity catalogHighlight = primaryActivity(9L, "Brandenburger Tor", InterestType.SIGHTSEEING, sightseeing);
        catalogHighlight.active = false;
        catalogHighlight.importVersion = 0;

        PlanningService service = serviceWithActivities(catalogHighlight);
        service.sync = noRefreshSync();
        TripDayEntity day = emptyDay();
        day.trip.selectedInterests = new HashSet<>(Set.of(sightseeing));

        service.generatePlan(day.trip, List.of(6L), Set.of(InterestType.SIGHTSEEING));

        assertTrue(day.activities.isEmpty());
    }

    @Test
    void generatePlanExtendsBalancedWindowAndSchedulesNightlife() {
        InterestEntity nightlife = interest(9L, "Nachtleben");
        nightlife.code = InterestType.NIGHTLIFE.name();
        ActivityEntity bar = primaryActivity(42L, "Bar", InterestType.NIGHTLIFE, nightlife);
        PlanningService service = serviceWithActivities(bar);
        service.sync = noRefreshSync();
        TripDayEntity day = emptyDay();
        day.trip.selectedInterests = new HashSet<>(Set.of(nightlife));

        service.generatePlan(day.trip, List.of(9L), Set.of(InterestType.NIGHTLIFE));

        assertEquals(1440, day.availableUntil);
        assertEquals(1, day.activities.size());
        assertEquals(1200, day.activities.get(0).scheduledStart);
        assertEquals(180, day.activities.get(0).durationMinutes);
    }

    @Test
    void generatePlanReallocatesWhenNatureHasOnlyOldInvalidCandidates() {
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();
        InterestEntity food = interest(4L, "Essen & Cafes");
        food.code = InterestType.FOOD.name();

        ActivityEntity oldViewpoint = primaryActivity(5L, "Station Platform Viewpoint", InterestType.NATURE, nature);
        oldViewpoint.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION - 1;
        ActivityEntity restaurant = primaryActivity(6L, "Restaurant", InterestType.FOOD, food);

        PlanningService service = serviceWithActivities(oldViewpoint, restaurant);
        service.sync = noRefreshSync();
        TripDayEntity day = emptyDay();
        day.trip.selectedInterests = new HashSet<>(Set.of(nature, food));

        service.generatePlan(day.trip, List.of(3L, 4L), Set.of(InterestType.NATURE, InterestType.FOOD));

        assertTrue(day.activities.stream().noneMatch(item -> item.activity == oldViewpoint));
        assertTrue(day.activities.stream().anyMatch(item -> item.activity == restaurant));
    }

    @Test
    void flexibleReallocationUsesOtherValidInterestsWhenNaturePoolIsSmall() {
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();
        InterestEntity culture = interest(1L, "Kultur & Museen");
        culture.code = InterestType.CULTURE.name();
        InterestEntity food = interest(4L, "Essen & Cafes");
        food.code = InterestType.FOOD.name();

        ActivityEntity park = primaryActivity(1L, "Park", InterestType.NATURE, nature);
        List<ActivityEntity> cultureActivities = java.util.stream.IntStream.range(0, 4)
            .mapToObj(index -> primaryActivity(10L + index, "Museum " + index, InterestType.CULTURE, culture))
            .toList();
        List<ActivityEntity> foodActivities = java.util.stream.IntStream.range(0, 4)
            .mapToObj(index -> primaryActivity(20L + index, "Restaurant " + index, InterestType.FOOD, food))
            .toList();
        ActivityEntity[] activities = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(park),
            java.util.stream.Stream.concat(cultureActivities.stream(), foodActivities.stream())
        ).toArray(ActivityEntity[]::new);
        PlanningService service = serviceWithActivities(activities);
        service.sync = noRefreshSync();
        TripEntity trip = tripWithDays(3, de.travelmate.trip.TripPace.BALANCED);
        trip.selectedInterests = new HashSet<>(Set.of(nature, culture, food));

        service.generatePlan(trip, List.of(1L, 3L, 4L), Set.of(InterestType.NATURE, InterestType.CULTURE, InterestType.FOOD));

        long scheduledNature = scheduledCount(trip, InterestType.NATURE);
        long scheduledCulture = scheduledCount(trip, InterestType.CULTURE);
        long scheduledFood = scheduledCount(trip, InterestType.FOOD);
        assertEquals(9, trip.days.stream().mapToInt(day -> day.activities.size()).sum());
        assertEquals(1, scheduledNature);
        assertEquals(8, scheduledCulture + scheduledFood);
    }

    @Test
    void roundRobinKeepsLaterDaysFromStayingEmptyWhenPoolIsLimited() {
        InterestEntity culture = interest(1L, "Kultur & Museen");
        culture.code = InterestType.CULTURE.name();
        ActivityEntity[] activities = java.util.stream.IntStream.range(0, 12)
            .mapToObj(index -> primaryActivity(100L + index, "Museum " + index, InterestType.CULTURE, culture))
            .toArray(ActivityEntity[]::new);
        PlanningService service = serviceWithActivities(activities);
        service.sync = noRefreshSync();
        TripEntity trip = tripWithDays(6, de.travelmate.trip.TripPace.BALANCED);
        trip.selectedInterests = new HashSet<>(Set.of(culture));

        service.generatePlan(trip, List.of(1L), Set.of(InterestType.CULTURE));

        assertEquals(12, trip.days.stream().mapToInt(day -> day.activities.size()).sum());
        assertTrue(trip.days.stream().allMatch(day -> day.activities.size() == 2));
    }

    @Test
    void fillMissingPlanKeepsExistingActivitiesAndFillsNewDays() {
        InterestEntity culture = interest(1L, "Kultur & Museen");
        culture.code = InterestType.CULTURE.name();
        ActivityEntity existing = primaryActivity(1L, "Existing Museum", InterestType.CULTURE, culture);
        ActivityEntity[] activities = java.util.stream.IntStream.range(0, 8)
            .mapToObj(index -> primaryActivity(10L + index, "Museum " + index, InterestType.CULTURE, culture))
            .toArray(ActivityEntity[]::new);
        PlanningService service = serviceWithActivities(
            java.util.stream.Stream.concat(java.util.stream.Stream.of(existing), java.util.Arrays.stream(activities))
                .toArray(ActivityEntity[]::new)
        );
        service.sync = noRefreshSync();
        TripEntity trip = tripWithDays(2, de.travelmate.trip.TripPace.BALANCED);
        trip.selectedInterests = new HashSet<>(Set.of(culture));
        TripDayActivityEntity existingItem = new TripDayActivityEntity();
        existingItem.id = 99L;
        existingItem.tripDay = trip.days.getFirst();
        existingItem.activity = existing;
        existingItem.position = 1;
        existingItem.scheduledStart = 600;
        existingItem.durationMinutes = 90;
        trip.days.getFirst().activities.add(existingItem);

        service.fillMissingPlan(trip, List.of(1L), Set.of(InterestType.CULTURE));

        assertTrue(trip.days.getFirst().activities.contains(existingItem));
        assertTrue(trip.days.get(1).activities.size() > 0);
    }

    @Test
    void dailyInterestCapSearchesOtherAreasBeforeRepeatingShopping() {
        InterestEntity culture = interest(1L, "Kultur & Museen");
        culture.code = InterestType.CULTURE.name();
        InterestEntity food = interest(2L, "Essen & Cafes");
        food.code = InterestType.FOOD.name();
        InterestEntity nature = interest(3L, "Natur & Outdoor");
        nature.code = InterestType.NATURE.name();
        InterestEntity shopping = interest(4L, "Shopping & Maerkte");
        shopping.code = InterestType.SHOPPING.name();

        ActivityEntity[] activities = new ActivityEntity[] {
            locatedPrimaryActivity(1L, "West Mall", InterestType.SHOPPING, shopping, 52.5000, 13.3000),
            locatedPrimaryActivity(2L, "West Market", InterestType.SHOPPING, shopping, 52.5010, 13.3010),
            locatedPrimaryActivity(3L, "West Arcade", InterestType.SHOPPING, shopping, 52.5020, 13.3020),
            locatedPrimaryActivity(4L, "West Department Store", InterestType.SHOPPING, shopping, 52.5030, 13.3030),
            locatedPrimaryActivity(5L, "Central Museum", InterestType.CULTURE, culture, 52.5200, 13.4050),
            locatedPrimaryActivity(6L, "Central Cafe", InterestType.FOOD, food, 52.5205, 13.4055),
            locatedPrimaryActivity(7L, "Central Park", InterestType.NATURE, nature, 52.5210, 13.4060)
        };
        PlanningService service = serviceWithActivities(activities);
        service.sync = noRefreshSync();
        TripEntity trip = tripWithDays(1, de.travelmate.trip.TripPace.ACTIVE);
        trip.latitude = 52.5200;
        trip.longitude = 13.4050;
        trip.selectedInterests = new HashSet<>(Set.of(culture, food, nature, shopping));

        service.generatePlan(trip, List.of(1L, 2L, 3L, 4L), Set.of(
            InterestType.CULTURE,
            InterestType.FOOD,
            InterestType.NATURE,
            InterestType.SHOPPING
        ));

        assertEquals(4, trip.days.getFirst().activities.size());
        assertTrue(scheduledCount(trip, InterestType.SHOPPING) <= 2);
        assertTrue(trip.days.getFirst().activities.stream()
            .map(item -> item.activity.primaryInterest)
            .collect(java.util.stream.Collectors.toSet())
            .size() >= 3);
    }

    @Test
    void dailyInterestCapRelaxesWhenOnlyOneInterestCanFillTheDay() {
        InterestEntity shopping = interest(4L, "Shopping & Maerkte");
        shopping.code = InterestType.SHOPPING.name();
        ActivityEntity[] activities = java.util.stream.IntStream.range(0, 4)
            .mapToObj(index -> locatedPrimaryActivity(
                300L + index,
                "Shop " + index,
                InterestType.SHOPPING,
                shopping,
                52.5000 + index * 0.0005,
                13.3000 + index * 0.0005
            ))
            .toArray(ActivityEntity[]::new);
        PlanningService service = serviceWithActivities(activities);
        service.sync = noRefreshSync();
        TripEntity trip = tripWithDays(1, de.travelmate.trip.TripPace.ACTIVE);
        trip.latitude = 52.5200;
        trip.longitude = 13.4050;
        trip.selectedInterests = new HashSet<>(Set.of(shopping));

        service.generatePlan(trip, List.of(4L), Set.of(InterestType.SHOPPING));

        assertEquals(4, trip.days.getFirst().activities.size());
        assertEquals(4, scheduledCount(trip, InterestType.SHOPPING));
    }

    private ActivityEntity activity(Double rating, double quality) {
        ActivityEntity activity = new ActivityEntity();
        activity.rating = rating;
        activity.dataQualityScore = quality;
        activity.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION;
        return activity;
    }

    private ActivityEntity activity(Long id, String name, Double rating, double quality) {
        ActivityEntity activity = activity(rating, quality);
        activity.id = id;
        activity.name = name;
        activity.category = "Kultur";
        return activity;
    }

    private PlanningService serviceWithActivities(ActivityEntity... available) {
        PlanningService service = new PlanningService();
        service.activities = new ActivityRepository() {
            @Override
            public List<ActivityEntity> findByCity(String city) {
                return List.of(available);
            }

            @Override
            public List<ActivityEntity> findActiveByCity(String city) {
                return List.of(available);
            }
        };
        service.timeRules = new ActivityTimeRules();
        return service;
    }

    private TripDayEntity emptyDay() {
        TripEntity trip = new TripEntity();
        trip.city = "Berlin";
        trip.daysCount = 1;
        TripDayEntity day = new TripDayEntity();
        day.id = 1L;
        day.trip = trip;
        day.dayNumber = 1;
        day.availableFrom = 540;
        day.availableUntil = 1200;
        trip.days.add(day);
        return day;
    }

    private TripEntity tripWithDays(int days, de.travelmate.trip.TripPace pace) {
        TripEntity trip = new TripEntity();
        trip.city = "Berlin";
        trip.daysCount = days;
        trip.pace = pace;
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            TripDayEntity day = new TripDayEntity();
            day.id = (long) dayNumber;
            day.dayNumber = dayNumber;
            day.trip = trip;
            day.availableFrom = 540;
            day.availableUntil = 1200;
            trip.days.add(day);
        }
        return trip;
    }

    private TripDayActivityEntity scheduledItem(ActivityEntity activity) {
        TripDayEntity day = emptyDay();
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.id = activity.id * 10;
        item.tripDay = day;
        item.activity = activity;
        item.position = 1;
        item.scheduledStart = 600;
        item.durationMinutes = 90;
        day.activities.add(item);
        return item;
    }

    private InterestEntity interest(Long id, String name) {
        InterestEntity interest = new InterestEntity();
        interest.id = id;
        interest.name = name;
        return interest;
    }

    private ActivityEntity primaryActivity(
        Long id,
        String name,
        InterestType type,
        InterestEntity interest
    ) {
        ActivityEntity activity = activity(id, name, null, 1.0);
        activity.primaryInterest = type;
        activity.interestScores.add(mapping(activity, interest, 10));
        return activity;
    }

    private ActivityEntity locatedPrimaryActivity(
        Long id,
        String name,
        InterestType type,
        InterestEntity interest,
        double lat,
        double lon
    ) {
        ActivityEntity activity = primaryActivity(id, name, type, interest);
        activity.latitude = lat;
        activity.longitude = lon;
        activity.finalScore = 0.8;
        activity.categoryFitScore = 1;
        activity.itineraryFitScore = 1;
        activity.dataQualityScore = 0.8;
        return activity;
    }

    private ActivityInterestEntity mapping(ActivityEntity activity, InterestEntity interest, int score) {
        ActivityInterestEntity mapping = new ActivityInterestEntity();
        mapping.activity = activity;
        mapping.interest = interest;
        mapping.score = score;
        return mapping;
    }

    private long scheduledCount(TripEntity trip, InterestType type) {
        return trip.days.stream()
            .flatMap(day -> day.activities.stream())
            .filter(item -> item.activity.primaryInterest == type)
            .count();
    }

    private ActivitySyncService noRefreshSync() {
        return new ActivitySyncService() {
            @Override
            public boolean needsRefresh(String city, Set<InterestType> interests) {
                return false;
            }

            @Override
            public RefreshDecision refreshDecision(
                String city,
                Set<InterestType> interests,
                de.travelmate.activity.ImportDemand demand,
                Double latitude,
                Double longitude
            ) {
                return RefreshDecision.NO_REFRESH_NEEDED;
            }
        };
    }
}
