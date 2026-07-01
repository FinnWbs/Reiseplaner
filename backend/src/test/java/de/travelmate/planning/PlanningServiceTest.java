package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityInterestEntity;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.sync.ActivitySyncService;
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
        TripDayEntity day = new TripDayEntity();
        day.id = 1L;
        day.trip = trip;
        day.availableFrom = 540;
        day.availableUntil = 1200;
        trip.days.add(day);
        return day;
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

    private ActivityInterestEntity mapping(ActivityEntity activity, InterestEntity interest, int score) {
        ActivityInterestEntity mapping = new ActivityInterestEntity();
        mapping.activity = activity;
        mapping.interest = interest;
        mapping.score = score;
        return mapping;
    }

    private ActivitySyncService noRefreshSync() {
        return new ActivitySyncService() {
            @Override
            public boolean needsRefresh(String city, Set<InterestType> interests) {
                return false;
            }
        };
    }
}
