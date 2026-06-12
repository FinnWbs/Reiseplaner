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
    private static final int ACTIVITIES_PER_DAY = 2;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivitySyncService sync;

    public void generatePlan(TripEntity trip, List<Long> interestIds) {
        if (sync.cityNeedsRefresh(trip.city)) {
            sync.syncCity(trip.city);
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
        int selectedLimit = trip.daysCount * ACTIVITIES_PER_DAY;
        List<ActivityEntity> selected = scored.stream()
            .map(ScoredActivity::activity)
            .filter(activity -> alreadyUsed.add(activity.id))
            .limit(selectedLimit)
            .toList();

        int cursor = 0;
        for (ActivityEntity activity : selected) {
            TripDayEntity day = nextDayWithSpace(trip, cursor);
            if (day == null) {
                break;
            }
            TripDayActivityEntity item = new TripDayActivityEntity();
            item.tripDay = day;
            item.activity = activity;
            item.position = day.activities.size() + 1;
            item.locked = false;
            day.activities.add(item);
            cursor = day.dayNumber;
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

    private void ensureDays(TripEntity trip) {
        for (int i = trip.days.size() + 1; i <= trip.daysCount; i++) {
            TripDayEntity day = new TripDayEntity();
            day.trip = trip;
            day.dayNumber = i;
            trip.days.add(day);
        }
    }

    private void removeUnlockedActivities(TripEntity trip) {
        for (TripDayEntity day : trip.days) {
            day.activities.removeIf(item -> !item.locked);
            int position = 1;
            for (TripDayActivityEntity item : day.activities) {
                item.position = position++;
            }
        }
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

    private TripDayEntity nextDayWithSpace(TripEntity trip, int cursor) {
        for (int offset = 0; offset < trip.days.size(); offset++) {
            int index = (cursor + offset) % trip.days.size();
            TripDayEntity day = trip.days.get(index);
            if (day.activities.size() < ACTIVITIES_PER_DAY) {
                return day;
            }
        }
        return null;
    }
}
