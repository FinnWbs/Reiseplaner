package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.quality.PoiQualityEngine;
import de.travelmate.sync.ActivitySyncService;
import de.travelmate.trip.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

@ApplicationScoped
public class PlanningService {
    private static final int SLOT_GAP_MINUTES = 30;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivitySyncService sync;

    @Inject
    TripDayActivityRepository tripActivities;

    @Inject
    ActivityTimeRules timeRules;

    @Inject
    PoiQualityEngine qualityEngine;

    public void generatePlan(TripEntity trip, List<Long> interestIds) {
        generatePlan(trip, interestIds, Set.of());
    }

    public void generatePlan(TripEntity trip, List<Long> interestIds, Set<InterestType> requestedInterests) {
        Set<InterestType> selectedTypes = selectedTypes(trip, requestedInterests);
        if (sync.needsRefresh(trip.city, selectedTypes)) {
            sync.syncCity(trip.city, locationLookupText(trip), trip.placeId, trip.latitude, trip.longitude, selectedTypes);
        }

        Set<Long> selectedInterests = new HashSet<>(interestIds == null ? List.of() : interestIds);
        List<ScoredActivity> scored = activities.findActiveByCity(trip.city).stream()
            .filter(activity -> selectedTypes.isEmpty() || selectedTypes.contains(activity.primaryInterest))
            .map(activity -> score(activity, selectedInterests))
            .filter(item -> item.totalScore() > 0)
            .sorted(Comparator.comparingDouble(ScoredActivity::totalScore).reversed())
            .toList();

        ensureDays(trip);
        removeUnlockedActivities(trip);

        Set<Long> alreadyUsed = usedActivityIds(trip);
        Map<InterestType, Integer> quotas = quotas(selectedTypes, trip.days.size() * trip.pace.activitiesPerDay());
        Map<InterestType, Integer> scheduledByInterest = scheduledByInterest(trip);
        for (TripDayEntity day : trip.days) {
            scheduleLockedActivities(day);
            int target = trip.pace.activitiesPerDay();
            while (day.activities.size() < target) {
                SlotChoice choice = nextBalancedChoice(day, scored, selectedTypes, quotas, scheduledByInterest, alreadyUsed);
                if (choice == null) {
                    break;
                }
                addScheduledActivity(day, choice);
                alreadyUsed.add(choice.activity().id);
                if (choice.activity().primaryInterest != null) {
                    scheduledByInterest.merge(choice.activity().primaryInterest, 1, Integer::sum);
                }
            }
        }
        trip.status = TripStatus.PLANNED;
    }

    public ScoredActivity score(ActivityEntity activity, Set<Long> interestIds) {
        if (activity.importVersion != ActivityPersistenceService.CURRENT_IMPORT_VERSION) {
            return new ScoredActivity(activity, 0);
        }
        int interestScore = activity.interestScores.stream()
            .filter(mapping -> interestIds.contains(mapping.interest.id))
            .filter(mapping -> activity.primaryInterest == null || mapping.interest.code.equals(activity.primaryInterest.name()))
            .mapToInt(mapping -> mapping.score)
            .max()
            .orElse(0);
        if (interestIds != null && !interestIds.isEmpty() && interestScore <= 0) {
            return new ScoredActivity(activity, 0);
        }

        double ratingScore = activity.rating == null ? 0 : Math.max(0, Math.min(10, activity.rating * 2));
        double qualityScore = Math.max(0, Math.min(10, activity.dataQualityScore * 10));
        double legacyScore = (interestScore * 0.6 + ratingScore * 0.2 + qualityScore * 0.2) / 10;
        double storedFinalScore = activity.finalScore > 0 ? activity.finalScore : legacyScore;
        double categoryFitScore = activity.categoryFitScore > 0
            ? activity.categoryFitScore
            : Math.max(0, Math.min(1, interestScore / 10.0));
        double itineraryFitScore = activity.itineraryFitScore > 0 ? activity.itineraryFitScore : 1;
        double total = 10 * engine().planningScore(storedFinalScore, categoryFitScore, itineraryFitScore, 1);
        return new ScoredActivity(activity, total);
    }

    public void scheduleDay(TripDayEntity day, boolean lockItems) {
        day.activities.sort(Comparator.comparingInt(item -> item.position));
        int cursor = day.availableFrom;
        for (int index = 0; index < day.activities.size(); index++) {
            TripDayActivityEntity item = day.activities.get(index);
            ActivityTimeRules.TimeProfile profile = timeRules.profile(item.activity);
            int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
            if (start < profile.preferredStart() && profile.preferredStart() + item.durationMinutes <= day.availableUntil) {
                start = profile.preferredStart();
            }
            start = Math.min(start, 1440 - item.durationMinutes);
            item.position = index + 1;
            item.scheduledStart = start;
            if (lockItems) {
                item.locked = true;
            }
            cursor = start + item.durationMinutes + SLOT_GAP_MINUTES;
        }
    }

    public Optional<ActivityEntity> replacementFor(
        TripEntity trip,
        TripDayActivityEntity item,
        Set<Long> interestIds
    ) {
        Set<Long> used = usedActivityIds(trip);
        Set<InterestType> selectedTypes = selectedTypes(trip, Set.of());
        return activities.findActiveByCity(trip.city).stream()
            .filter(activity -> selectedTypes.isEmpty() || selectedTypes.contains(activity.primaryInterest))
            .filter(activity -> isReplacementCandidate(activity, item, used))
            .map(activity -> score(activity, interestIds))
            .filter(scored -> scored.totalScore() > 0)
            .sorted(Comparator.comparingDouble(ScoredActivity::totalScore).reversed())
            .map(ScoredActivity::activity)
            .findFirst();
    }

    private boolean isReplacementCandidate(
        ActivityEntity activity,
        TripDayActivityEntity item,
        Set<Long> usedActivityIds
    ) {
        return !usedActivityIds.contains(activity.id)
            && timeRules.fitsAt(activity, item.scheduledStart, item.durationMinutes);
    }

    private SlotChoice nextBalancedChoice(
        TripDayEntity day,
        List<ScoredActivity> scored,
        Set<InterestType> selectedTypes,
        Map<InterestType, Integer> quotas,
        Map<InterestType, Integer> scheduledByInterest,
        Set<Long> alreadyUsed
    ) {
        Set<InterestType> usedToday = day.activities.stream()
            .map(item -> item.activity.primaryInterest)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        List<InterestType> orderedTypes = selectedTypes.stream()
            .sorted(Comparator.comparingInt((InterestType type) ->
                quotas.getOrDefault(type, 0) - scheduledByInterest.getOrDefault(type, 0)
            ).reversed().thenComparing(Enum::name))
            .toList();

        SlotChoice choice = firstChoice(day, scored, orderedTypes, usedToday, alreadyUsed, true, quotas, scheduledByInterest);
        if (choice != null) return choice;
        choice = firstChoice(day, scored, orderedTypes, usedToday, alreadyUsed, false, quotas, scheduledByInterest);
        if (choice != null) return choice;
        return firstChoice(day, scored, orderedTypes, Set.of(), alreadyUsed, false, quotas, scheduledByInterest);
    }

    private SlotChoice firstChoice(
        TripDayEntity day,
        List<ScoredActivity> scored,
        List<InterestType> orderedTypes,
        Set<InterestType> usedToday,
        Set<Long> alreadyUsed,
        boolean requireNewDayCategory,
        Map<InterestType, Integer> quotas,
        Map<InterestType, Integer> scheduledByInterest
    ) {
        for (InterestType type : orderedTypes) {
            if (requireNewDayCategory && usedToday.contains(type)) continue;
            if (quotas.getOrDefault(type, 0) <= scheduledByInterest.getOrDefault(type, 0)) continue;
            Optional<SlotChoice> choice = scored.stream()
                .map(ScoredActivity::activity)
                .filter(activity -> activity.primaryInterest == type && !alreadyUsed.contains(activity.id))
                .map(activity -> slotFor(day, activity))
                .flatMap(Optional::stream)
                .findFirst();
            if (choice.isPresent()) return choice.get();
        }
        return null;
    }

    private Map<InterestType, Integer> quotas(Set<InterestType> selectedTypes, int slots) {
        Map<InterestType, Integer> quotas = new EnumMap<>(InterestType.class);
        if (selectedTypes.isEmpty()) return quotas;
        List<InterestType> ordered = selectedTypes.stream().sorted(Comparator.comparing(Enum::name)).toList();
        int base = slots / ordered.size();
        int remainder = slots % ordered.size();
        for (int index = 0; index < ordered.size(); index++) {
            quotas.put(ordered.get(index), base + (index < remainder ? 1 : 0));
        }
        return quotas;
    }

    private Map<InterestType, Integer> scheduledByInterest(TripEntity trip) {
        Map<InterestType, Integer> counts = new EnumMap<>(InterestType.class);
        trip.days.stream().flatMap(day -> day.activities.stream())
            .map(item -> item.activity.primaryInterest)
            .filter(Objects::nonNull)
            .forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private Set<InterestType> selectedTypes(TripEntity trip, Set<InterestType> requestedTypes) {
        if (requestedTypes != null && !requestedTypes.isEmpty()) return requestedTypes;
        return trip.selectedInterests.stream()
            .map(interest -> InterestType.valueOf(interest.code))
            .collect(java.util.stream.Collectors.toSet());
    }

    private void ensureDays(TripEntity trip) {
        for (int i = trip.days.size() + 1; i <= trip.daysCount; i++) {
            TripDayEntity day = new TripDayEntity();
            day.trip = trip;
            day.dayNumber = i;
            day.availableFrom = trip.dayRhythm.availableFrom();
            day.availableUntil = trip.dayRhythm.availableUntil();
            trip.days.add(day);
        }
    }

    private void removeUnlockedActivities(TripEntity trip) {
        List<TripDayActivityEntity> removed = trip.days.stream()
            .flatMap(day -> day.activities.stream())
            .filter(item -> !item.locked)
            .toList();
        if (removed.isEmpty()) return;
        for (TripDayActivityEntity item : removed) {
            item.tripDay.activities.remove(item);
            tripActivities.delete(item);
        }
        tripActivities.flush();
        trip.days.forEach(this::renumber);
    }

    private void scheduleLockedActivities(TripDayEntity day) {
        day.activities.sort(Comparator.comparingInt(item -> item.position));
        int cursor = day.availableFrom;
        for (TripDayActivityEntity item : day.activities) {
            cursor = Math.max(cursor, item.scheduledStart + item.durationMinutes + SLOT_GAP_MINUTES);
        }
    }

    private Optional<SlotChoice> slotFor(TripDayEntity day, ActivityEntity activity) {
        ActivityTimeRules.TimeProfile profile = timeRules.profile(activity);
        int cursor = day.activities.stream()
            .mapToInt(item -> item.scheduledStart + item.durationMinutes + SLOT_GAP_MINUTES)
            .max()
            .orElse(day.availableFrom);
        int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
        if (start < profile.preferredStart()
            && profile.preferredStart() >= day.availableFrom
            && profile.preferredStart() + profile.durationMinutes() <= day.availableUntil) {
            start = profile.preferredStart();
        }
        int latestEnd = Math.min(day.availableUntil, profile.latestEnd());
        return start + profile.durationMinutes() <= latestEnd
            ? Optional.of(new SlotChoice(activity, start, profile.durationMinutes()))
            : Optional.empty();
    }

    private void addScheduledActivity(TripDayEntity day, SlotChoice choice) {
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.tripDay = day;
        item.activity = choice.activity();
        item.position = day.activities.size() + 1;
        item.scheduledStart = choice.start();
        item.durationMinutes = choice.duration();
        item.locked = false;
        day.activities.add(item);
    }

    private Set<Long> usedActivityIds(TripEntity trip) {
        Set<Long> used = new HashSet<>();
        for (TripDayEntity day : trip.days) {
            for (TripDayActivityEntity item : day.activities) {
                used.add(item.activity.id);
            }
        }
        return used;
    }

    private void renumber(TripDayEntity day) {
        day.activities.sort(Comparator.comparingInt(item -> item.position));
        for (int i = 0; i < day.activities.size(); i++) {
            day.activities.get(i).position = i + 1;
        }
    }

    private String locationLookupText(TripEntity trip) {
        if (trip.country != null && !trip.country.isBlank()) {
            return trip.city + ", " + trip.country;
        }
        if (trip.countryCode != null && !trip.countryCode.isBlank()) {
            return trip.city + ", " + trip.countryCode;
        }
        return trip.city;
    }

    private PoiQualityEngine engine() {
        return qualityEngine == null ? new PoiQualityEngine() : qualityEngine;
    }

    private record SlotChoice(ActivityEntity activity, int start, int duration) {}
}
