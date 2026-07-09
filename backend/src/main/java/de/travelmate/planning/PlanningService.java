package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ImportDemand;
import de.travelmate.activity.ImportDemandService;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.quality.PoiQualityEngine;
import de.travelmate.sync.ActivitySyncService;
import de.travelmate.sync.RefreshDecision;
import de.travelmate.trip.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.util.*;

@ApplicationScoped
public class PlanningService {
    private static final Logger LOG = Logger.getLogger(PlanningService.class);

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

    @Inject
    ImportDemandService importDemandService;

    @Inject
    SpatialPlanningService spatialPlanningService;

    @Inject
    SpatialDiagnosticsService spatialDiagnosticsService;

    @Inject
    TripTimeWindowPolicy timeWindowPolicy;

    @Inject
    SpatialPlanningSettings spatialSettings;

    @Inject
    InterestQuotaAllocator quotaAllocator;

    @Inject
    DayScheduler dayScheduler;

    @Inject
    SlotSelectionPolicy slotSelectionPolicy;

    private SpatialDiagnostics lastSpatialDiagnostics;

    public void generatePlan(TripEntity trip, List<Long> interestIds) {
        generatePlan(trip, interestIds, Set.of());
    }

    public void generatePlan(TripEntity trip, List<Long> interestIds, Set<InterestType> requestedInterests) {
        Set<InterestType> selectedTypes = selectedTypes(trip, requestedInterests);
        timeWindowPolicy().extendDaysForInterests(trip, selectedTypes);
        ImportDemand demand = demandFor(trip, selectedTypes);
        RefreshDecision refreshDecision = sync.refreshDecision(trip.city, selectedTypes, demand, trip.latitude, trip.longitude);
        if (refreshDecision.importRequired()) {
            sync.syncCity(trip.city, locationLookupText(trip), trip.placeId, trip.latitude, trip.longitude, selectedTypes, demand);
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
        Map<InterestType, Integer> availableByInterest = quotaAllocator().availableByInterest(scored, selectedTypes, alreadyUsed);
        Map<InterestType, Integer> scheduledByInterest = quotaAllocator().scheduledByInterest(trip);
        Map<InterestType, Integer> quotas = quotaAllocator().reallocatedQuotas(
            selectedTypes,
            trip.days.size() * trip.pace.activitiesPerDay(),
            availableByInterest,
            scheduledByInterest
        );
        List<TripDayEntity> orderedDays = trip.days.stream()
            .sorted(Comparator.comparingInt(day -> day.dayNumber))
            .toList();
        SpatialPlanningContext spatialContext = spatialContextFor(trip, scored, orderedDays);
        int target = trip.pace.activitiesPerDay();
        for (int slotIndex = 0; slotIndex < target; slotIndex++) {
            for (TripDayEntity day : orderedDays) {
                if (day.activities.size() > slotIndex || day.activities.size() >= target) {
                    continue;
                }
                PlanSlotChoice choice = slotSelection().nextBalancedChoice(
                    day,
                    scored,
                    selectedTypes,
                    quotas,
                    scheduledByInterest,
                    alreadyUsed,
                    spatialContext,
                    orderedDays
                );
                if (choice == null) {
                    continue;
                }
                addScheduledActivity(day, choice);
                alreadyUsed.add(choice.activity().id);
                if (choice.activity().primaryInterest != null) {
                    scheduledByInterest.merge(choice.activity().primaryInterest, 1, Integer::sum);
                }
            }
        }
        trip.status = TripStatus.PLANNED;
        lastSpatialDiagnostics = diagnostics().analyze(trip);
        logSpatialDiagnostics(trip, lastSpatialDiagnostics, quotas);
    }

    public Optional<SpatialDiagnostics> lastSpatialDiagnostics() {
        return Optional.ofNullable(lastSpatialDiagnostics);
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
        scheduler().scheduleDay(day, lockItems);
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
        trip.days.forEach(day -> scheduler().renumber(day));
    }

    private void addScheduledActivity(TripDayEntity day, PlanSlotChoice choice) {
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

    private ImportDemand demandFor(TripEntity trip, Set<InterestType> selectedTypes) {
        ImportDemandService service = importDemandService == null ? new ImportDemandService() : importDemandService;
        return service.forTrip(trip.city, selectedTypes, trip.daysCount > 0 ? trip.daysCount : trip.days.size(), trip.pace);
    }

    private SpatialPlanningContext spatialContextFor(
        TripEntity trip,
        List<ScoredActivity> scored,
        List<TripDayEntity> orderedDays
    ) {
        SpatialPlanningService service = spatialPlanningService == null ? new SpatialPlanningService() : spatialPlanningService;
        return service.createContext(trip, scored, orderedDays);
    }

    private SpatialDiagnosticsService diagnostics() {
        return spatialDiagnosticsService == null ? new SpatialDiagnosticsService() : spatialDiagnosticsService;
    }

    private SpatialPlanningSettings settings() {
        return spatialSettings == null ? new SpatialPlanningSettings() : spatialSettings;
    }

    private TripTimeWindowPolicy timeWindowPolicy() {
        return timeWindowPolicy == null ? new TripTimeWindowPolicy() : timeWindowPolicy;
    }

    private InterestQuotaAllocator quotaAllocator() {
        return quotaAllocator == null ? new InterestQuotaAllocator() : quotaAllocator;
    }

    private DayScheduler scheduler() {
        return dayScheduler == null ? new DayScheduler() : dayScheduler;
    }

    private SlotSelectionPolicy slotSelection() {
        if (slotSelectionPolicy == null) {
            SlotSelectionPolicy fallback = new SlotSelectionPolicy();
            fallback.spatialSettings = settings();
            fallback.dayScheduler = scheduler();
            return fallback;
        }
        return slotSelectionPolicy;
    }

    private void logSpatialDiagnostics(
        TripEntity trip,
        SpatialDiagnostics diagnostics,
        Map<InterestType, Integer> plannedInterestDistribution
    ) {
        LOG.infof(
            "Trip %s %d days: activities=%d clusters=%d dominantClusterShare=%.2f avgDistanceFromCenter=%.1fkm plannedInterests=%s actualInterests=%s dominantInterestDays=%s warnings=%s",
            diagnostics.cityName(),
            diagnostics.tripDays(),
            diagnostics.totalActivities(),
            diagnostics.uniqueSpatialClusters(),
            diagnostics.dominantClusterShare(),
            diagnostics.averageDistanceFromCityCenterKm(),
            plannedInterestDistribution,
            actualInterestDistribution(trip),
            dominantInterestDays(trip),
            diagnostics.warnings()
        );
    }

    private Map<InterestType, Integer> actualInterestDistribution(TripEntity trip) {
        Map<InterestType, Integer> counts = new EnumMap<>(InterestType.class);
        trip.days.stream()
            .flatMap(day -> day.activities.stream())
            .map(item -> item.activity.primaryInterest)
            .filter(Objects::nonNull)
            .forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private Map<Integer, InterestType> dominantInterestDays(TripEntity trip) {
        Map<Integer, InterestType> dominant = new LinkedHashMap<>();
        trip.days.stream()
            .sorted(Comparator.comparingInt(day -> day.dayNumber))
            .forEach(day -> {
                Map<InterestType, Integer> counts = new EnumMap<>(InterestType.class);
                day.activities.stream()
                    .map(item -> item.activity.primaryInterest)
                    .filter(Objects::nonNull)
                    .forEach(type -> counts.merge(type, 1, Integer::sum));
                counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > settings().maxSameInterestPerDay())
                    .max(Map.Entry.<InterestType, Integer>comparingByValue().thenComparing(entry -> entry.getKey().name()))
                    .ifPresent(entry -> dominant.put(day.dayNumber, entry.getKey()));
            });
        return dominant;
    }

}
