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

    @Inject
    ImportDemandService importDemandService;

    @Inject
    SpatialPlanningService spatialPlanningService;

    @Inject
    SpatialDiagnosticsService spatialDiagnosticsService;

    @Inject
    SpatialPlanningSettings spatialSettings;

    private SpatialDiagnostics lastSpatialDiagnostics;

    public void generatePlan(TripEntity trip, List<Long> interestIds) {
        generatePlan(trip, interestIds, Set.of());
    }

    public void generatePlan(TripEntity trip, List<Long> interestIds, Set<InterestType> requestedInterests) {
        Set<InterestType> selectedTypes = selectedTypes(trip, requestedInterests);
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
        Map<InterestType, Integer> availableByInterest = availableByInterest(scored, selectedTypes, alreadyUsed);
        Map<InterestType, Integer> scheduledByInterest = scheduledByInterest(trip);
        Map<InterestType, Integer> quotas = reallocatedQuotas(
            selectedTypes,
            trip.days.size() * trip.pace.activitiesPerDay(),
            availableByInterest,
            scheduledByInterest
        );
        List<TripDayEntity> orderedDays = trip.days.stream()
            .sorted(Comparator.comparingInt(day -> day.dayNumber))
            .toList();
        for (TripDayEntity day : orderedDays) {
            scheduleLockedActivities(day);
        }
        SpatialPlanningContext spatialContext = spatialContextFor(trip, scored, orderedDays);
        int target = trip.pace.activitiesPerDay();
        for (int slotIndex = 0; slotIndex < target; slotIndex++) {
            for (TripDayEntity day : orderedDays) {
                if (day.activities.size() > slotIndex || day.activities.size() >= target) {
                    continue;
                }
                SlotChoice choice = nextBalancedChoice(
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
        Set<Long> alreadyUsed,
        SpatialPlanningContext spatialContext,
        List<TripDayEntity> orderedDays
    ) {
        Set<InterestType> usedToday = day.activities.stream()
            .map(item -> item.activity.primaryInterest)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<InterestType, Integer> dayInterestCounts = day.activities.stream()
            .map(item -> item.activity.primaryInterest)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toMap(
                type -> type,
                ignored -> 1,
                Integer::sum,
                () -> new EnumMap<>(InterestType.class)
            ));
        List<InterestType> orderedTypes = selectedTypes.stream()
            .sorted(Comparator.comparingInt((InterestType type) ->
                quotas.getOrDefault(type, 0) - scheduledByInterest.getOrDefault(type, 0)
            ).reversed().thenComparing(Enum::name))
            .toList();

        for (ChoiceConstraint constraint : choiceConstraints()) {
            for (SpatialCandidateMode mode : spatialModes(day, spatialContext)) {
                SlotChoice choice = firstChoice(
                    day,
                    scored,
                    orderedTypes,
                    usedToday,
                    dayInterestCounts,
                    alreadyUsed,
                    constraint,
                    quotas,
                    scheduledByInterest,
                    spatialContext,
                    orderedDays,
                    mode
                );
                if (choice != null) return choice;
            }
        }
        return null;
    }

    private SlotChoice firstChoice(
        TripDayEntity day,
        List<ScoredActivity> scored,
        List<InterestType> orderedTypes,
        Set<InterestType> usedToday,
        Map<InterestType, Integer> dayInterestCounts,
        Set<Long> alreadyUsed,
        ChoiceConstraint constraint,
        Map<InterestType, Integer> quotas,
        Map<InterestType, Integer> scheduledByInterest,
        SpatialPlanningContext spatialContext,
        List<TripDayEntity> orderedDays,
        SpatialCandidateMode mode
    ) {
        for (InterestType type : orderedTypes) {
            if (constraint.requireNewDayCategory() && usedToday.contains(type)) continue;
            if (constraint.enforceQuota() && quotas.getOrDefault(type, 0) <= scheduledByInterest.getOrDefault(type, 0)) continue;
            if (constraint.respectDailyInterestCap()
                && dayInterestCounts.getOrDefault(type, 0) >= settings().maxSameInterestPerDay()) {
                continue;
            }
            Optional<EvaluatedSlotChoice> choice = scored.stream()
                .filter(item -> item.activity().primaryInterest == type && !alreadyUsed.contains(item.activity().id))
                .filter(item -> matchesSpatialMode(mode, day, item.activity(), spatialContext))
                .filter(item -> !shouldDelayLateActivity(day, item.activity(), orderedTypes.size()))
                .map(item -> slotFor(day, item.activity())
                    .map(slot -> new EvaluatedSlotChoice(
                        slot,
                        spatialPlanningScore(item, day, spatialContext, orderedDays)
                    ))
                    .orElse(null))
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(EvaluatedSlotChoice::planningScore)
                    .thenComparing(item -> item.choice().activity().name == null ? "" : item.choice().activity().name));
            if (choice.isPresent()) return choice.get().choice();
        }
        return null;
    }

    private List<ChoiceConstraint> choiceConstraints() {
        return List.of(
            new ChoiceConstraint(true, true, true),
            new ChoiceConstraint(true, false, true),
            new ChoiceConstraint(false, true, true),
            new ChoiceConstraint(false, false, true),
            new ChoiceConstraint(false, false, false)
        );
    }

    private List<SpatialCandidateMode> spatialModes(TripDayEntity day, SpatialPlanningContext spatialContext) {
        if (spatialContext == null || spatialContext.uniqueClusters() < 2 || !spatialContext.hasPreferredCluster(day)) {
            return List.of(SpatialCandidateMode.ANY);
        }
        return List.of(
            SpatialCandidateMode.PREFERRED,
            SpatialCandidateMode.NEAR_PREFERRED,
            SpatialCandidateMode.OTHER_NON_CENTER,
            SpatialCandidateMode.CENTER,
            SpatialCandidateMode.ANY
        );
    }

    private boolean matchesSpatialMode(
        SpatialCandidateMode mode,
        TripDayEntity day,
        ActivityEntity activity,
        SpatialPlanningContext spatialContext
    ) {
        if (mode == SpatialCandidateMode.ANY || spatialContext == null) {
            return true;
        }
        return switch (mode) {
            case PREFERRED -> spatialContext.isPreferredCluster(day, activity);
            case NEAR_PREFERRED -> spatialContext.isNearPreferredCluster(day, activity);
            case OTHER_NON_CENTER -> spatialContext.isNonCenterCluster(activity);
            case CENTER -> spatialContext.isCenterCluster(activity);
            case ANY -> true;
        };
    }

    private boolean shouldDelayLateActivity(TripDayEntity day, ActivityEntity activity, int selectedTypeCount) {
        if (selectedTypeCount <= 1 || day.trip == null || day.trip.pace == null) {
            return false;
        }
        ActivityTimeRules.TimeProfile profile = timeRules.profile(activity);
        return profile.preferredStart() >= 1080
            && day.activities.size() < Math.max(0, day.trip.pace.activitiesPerDay() - 1);
    }

    private double spatialPlanningScore(
        ScoredActivity scored,
        TripDayEntity day,
        SpatialPlanningContext spatialContext,
        List<TripDayEntity> orderedDays
    ) {
        double adjusted = scored.totalScore();
        if (spatialContext == null || spatialContext.uniqueClusters() < 2) {
            return adjusted;
        }
        ActivityEntity activity = scored.activity();
        if (spatialContext.clusterId(activity).isEmpty()) {
            return Math.max(0, adjusted - 0.02);
        }
        if (spatialContext.isPreferredCluster(day, activity)) {
            adjusted += day.activities.isEmpty() ? 0.85 : 0.35;
        } else if (spatialContext.isNearPreferredCluster(day, activity)) {
            adjusted += day.activities.isEmpty() ? 0.30 : 0.12;
        } else if (spatialContext.preferredCluster(day).isPresent()) {
            adjusted -= day.activities.isEmpty() ? 0.22 : 0.08;
        }
        if (day.activities.isEmpty()) {
            adjusted += spatialContext.wasClusterUsedBeforeDay(activity, orderedDays, day.dayNumber) ? -0.05 : 0.05;
        } else if (spatialContext.isSameOrNearDayCluster(day, activity)) {
            adjusted += 0.18;
        } else if (spatialContext.isFarFromDayCluster(day, activity)) {
            adjusted -= 0.12;
        }
        if (spatialContext.isCenterCluster(activity)) {
            int allowedCenterActivities = Math.min(3, Math.max(1, orderedDays.size() / 3))
                * Math.max(1, day.trip.pace.activitiesPerDay());
            if (!spatialContext.isPreferredCluster(day, activity)) {
                adjusted -= 0.18;
            }
            if (spatialContext.centerClusterUseBeforeDay(orderedDays, day.dayNumber) >= allowedCenterActivities) {
                adjusted -= 0.30;
            }
        }
        return Math.max(0, adjusted);
    }

    private Map<InterestType, Integer> reallocatedQuotas(
        Set<InterestType> selectedTypes,
        int slots,
        Map<InterestType, Integer> availableByInterest,
        Map<InterestType, Integer> scheduledByInterest
    ) {
        Map<InterestType, Integer> desired = quotas(selectedTypes, slots);
        Map<InterestType, Integer> result = new EnumMap<>(InterestType.class);
        int assigned = 0;
        for (InterestType type : selectedTypes) {
            int capacity = availableByInterest.getOrDefault(type, 0) + scheduledByInterest.getOrDefault(type, 0);
            int quota = Math.min(desired.getOrDefault(type, 0), capacity);
            result.put(type, quota);
            assigned += quota;
        }
        int surplus = slots - assigned;
        while (surplus > 0) {
            Optional<InterestType> receiver = selectedTypes.stream()
                .filter(type -> result.getOrDefault(type, 0)
                    < availableByInterest.getOrDefault(type, 0) + scheduledByInterest.getOrDefault(type, 0))
                .sorted(Comparator.comparingInt((InterestType type) ->
                    availableByInterest.getOrDefault(type, 0) + scheduledByInterest.getOrDefault(type, 0)
                        - result.getOrDefault(type, 0)
                ).reversed().thenComparing(Enum::name))
                .findFirst();
            if (receiver.isEmpty()) {
                break;
            }
            result.merge(receiver.get(), 1, Integer::sum);
            surplus--;
        }
        return result;
    }

    private Map<InterestType, Integer> availableByInterest(
        List<ScoredActivity> scored,
        Set<InterestType> selectedTypes,
        Set<Long> alreadyUsed
    ) {
        Map<InterestType, Integer> available = new EnumMap<>(InterestType.class);
        scored.stream()
            .map(ScoredActivity::activity)
            .filter(activity -> activity.primaryInterest != null)
            .filter(activity -> selectedTypes.contains(activity.primaryInterest))
            .filter(activity -> !alreadyUsed.contains(activity.id))
            .forEach(activity -> available.merge(activity.primaryInterest, 1, Integer::sum));
        return available;
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

    private record SlotChoice(ActivityEntity activity, int start, int duration) {}
    private record EvaluatedSlotChoice(SlotChoice choice, double planningScore) {}
    private record ChoiceConstraint(
        boolean requireNewDayCategory,
        boolean enforceQuota,
        boolean respectDailyInterestCap
    ) {}
    private enum SpatialCandidateMode {
        PREFERRED,
        NEAR_PREFERRED,
        OTHER_NON_CENTER,
        CENTER,
        ANY
    }
}
