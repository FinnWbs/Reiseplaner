package de.travelmate.activity;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ImportDemandService {
    @Inject
    ActivityImportSettings settings;

    public ImportDemand forTrip(String city, Set<InterestType> selectedInterests, int tripDays, TripPace pace) {
        Set<InterestType> interests = selectedInterests == null || selectedInterests.isEmpty()
            ? InterestType.primaryTypes()
            : selectedInterests.stream().filter(InterestType::isPrimary).collect(Collectors.toUnmodifiableSet());
        if (interests.isEmpty()) {
            interests = InterestType.primaryTypes();
        }
        TripPace resolvedPace = pace == null ? TripPace.BALANCED : pace;
        int resolvedTripDays = Math.max(1, tripDays);
        int activitiesPerDay = resolvedPace.activitiesPerDay();
        int targetActivities = resolvedTripDays * activitiesPerDay;
        int eligiblePoolTarget = ceil(targetActivities * settings().eligiblePoolMultiplier());
        int rawPoolTargetTotal = Math.min(
            ceil(eligiblePoolTarget / Math.max(0.01, settings().expectedYield())),
            settings().maxRawTotalPerTrip()
        );
        // 7 Tage
        // Temp = hoch = 4 Aktivitäten pro Tag
        // 3 Interessen ausgewählt - Natur, Geschichte, Kultur
        // 7 × 4 = 28 geplante Aktivitäten
        // 28 × 2,5 = 70 geeignete Aktivitäten im Pool
        // 70 / 0,35 = 200 Rohdaten-Treffer nötig
        // 200 / 3 = ~66 Rohdaten pro interesse
        // 66 * 0,35 = ~ 23 Aktivitäten notwenig
        Map<InterestType, Integer> rawTargetByInterest = new EnumMap<>(InterestType.class);
        Map<InterestType, Integer> eligibleTargetByInterest = new EnumMap<>(InterestType.class);
        double desiredSlotsForInterest = targetActivities / (double) interests.size();
        for (InterestType interest : interests) {
            int rawTarget = settings().clampRawPerInterest(ceil(desiredSlotsForInterest * settings().rawCandidatesPerNeededSlot()));
            rawTargetByInterest.put(interest, rawTarget);
            eligibleTargetByInterest.put(interest, Math.max(1, ceil(desiredSlotsForInterest * settings().eligiblePoolMultiplier())));
        }

        capTotal(rawTargetByInterest, settings().maxRawTotalPerTrip());
        return new ImportDemand(
            city,
            Set.copyOf(interests),
            resolvedTripDays,
            resolvedPace,
            activitiesPerDay,
            targetActivities,
            eligiblePoolTarget,
            rawPoolTargetTotal,
            Map.copyOf(rawTargetByInterest),
            Map.copyOf(eligibleTargetByInterest),
            settings().minSpatialClustersForTrip(resolvedTripDays),
            settings().maxDominantClusterShareForTrip(resolvedTripDays),
            minOuterDistanceBandCandidates(resolvedTripDays, targetActivities, interests.size()),
            resolvedTripDays > 1,
            settings().multiAreaEnabled()
        );
    }

    private static int minOuterDistanceBandCandidates(int tripDays, int targetActivities, int interestCount) {
        if (tripDays < 4) {
            return 0;
        }
        int perInterestTarget = Math.max(1, ceil(targetActivities / (double) Math.max(1, interestCount)));
        if (tripDays >= 11) {
            return Math.max(6, ceil(perInterestTarget * 0.35));
        }
        if (tripDays >= 7) {
            return Math.max(4, ceil(perInterestTarget * 0.30));
        }
        return Math.max(2, ceil(perInterestTarget * 0.25));
    }

    private void capTotal(Map<InterestType, Integer> targets, int maxTotal) {
        int total = targets.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= maxTotal || total == 0) {
            return;
        }
        double factor = maxTotal / (double) total;
        targets.replaceAll((interest, value) -> Math.max(settings().minRawPerInterest(), (int) Math.floor(value * factor)));
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private ActivityImportSettings settings() {
        return settings == null ? new ActivityImportSettings() : settings;
    }
}
