package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityRepository;
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

    public void generatePlan(TripEntity trip, List<Long> interestIds) {
        if (sync.cityNeedsRefresh(trip.city)) {
            sync.syncCity(trip.city, locationLookupText(trip), trip.placeId, trip.latitude, trip.longitude);
        }

        Set<Long> selectedInterests = new HashSet<>(interestIds == null ? List.of() : interestIds);
        List<ScoredActivity> scored = activities.findByCity(trip.city).stream()
            .map(activity -> score(activity, selectedInterests))
            .filter(item -> item.totalScore() > 0)
            .sorted(Comparator.comparingDouble(ScoredActivity::totalScore).reversed())
            .toList();

        ensureDays(trip);
        removeUnlockedActivities(trip);

        Set<Long> alreadyUsed = usedActivityIds(trip);
        for (TripDayEntity day : trip.days) {
            scheduleLockedActivities(day);
            int target = trip.pace.activitiesPerDay();
            while (day.activities.size() < target) {
                SlotChoice choice = scored.stream()
                    .map(ScoredActivity::activity)
                    .filter(activity -> !alreadyUsed.contains(activity.id))
                    .map(activity -> slotFor(day, activity))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(null);
                if (choice == null) {
                    break;
                }
                addScheduledActivity(day, choice);
                alreadyUsed.add(choice.activity().id);
            }
        }
        trip.status = TripStatus.PLANNED;
    }

    public ScoredActivity score(ActivityEntity activity, Set<Long> interestIds) {
        int interestScore = activity.interestScores.stream()
            .filter(mapping -> interestIds.contains(mapping.interest.id))
            .mapToInt(mapping -> mapping.score)
            .sum();

        double ratingScore = activity.rating == null ? 0 : Math.max(0, Math.min(10, activity.rating * 2));
        double qualityScore = Math.max(0, Math.min(10, activity.dataQualityScore * 10));
        double total = interestScore * 0.6 + ratingScore * 0.2 + qualityScore * 0.2;
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
        return activities.findByCity(trip.city).stream()
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

    private record SlotChoice(ActivityEntity activity, int start, int duration) {}
}
