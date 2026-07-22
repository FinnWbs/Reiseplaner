package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripDayEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class SlotSelectionPolicy {
    @Inject
    SpatialPlanningSettings spatialSettings;

    @Inject
    DayScheduler dayScheduler;

    PlanSlotChoice nextBalancedChoice(
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
                PlanSlotChoice choice = firstChoice(
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

    private PlanSlotChoice firstChoice(
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
            var choice = scored.stream()
                .filter(item -> item.activity().primaryInterest == type && !alreadyUsed.contains(item.activity().id))
                .filter(item -> matchesSpatialMode(mode, day, item.activity(), spatialContext))
                .filter(item -> !shouldDelayLateActivity(day, item.activity(), orderedTypes.size()))
                .map(item -> scheduler().slotFor(day, item.activity())
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
        ActivityTimeRules.TimeProfile profile = ActivityTimeRules.profile(activity);
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

    private SpatialPlanningSettings settings() {
        return spatialSettings == null ? new SpatialPlanningSettings() : spatialSettings;
    }

    private DayScheduler scheduler() {
        return dayScheduler == null ? new DayScheduler() : dayScheduler;
    }

    private record EvaluatedSlotChoice(PlanSlotChoice choice, double planningScore) {}
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
