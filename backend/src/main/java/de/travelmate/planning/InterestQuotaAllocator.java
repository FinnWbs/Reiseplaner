package de.travelmate.planning;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class InterestQuotaAllocator {
    Map<InterestType, Integer> reallocatedQuotas(
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
            var receiver = selectedTypes.stream()
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

    Map<InterestType, Integer> availableByInterest(
        Iterable<ScoredActivity> scored,
        Set<InterestType> selectedTypes,
        Set<Long> alreadyUsed
    ) {
        Map<InterestType, Integer> available = new EnumMap<>(InterestType.class);
        for (ScoredActivity item : scored) {
            var activity = item.activity();
            if (activity.primaryInterest != null
                && selectedTypes.contains(activity.primaryInterest)
                && !alreadyUsed.contains(activity.id)) {
                available.merge(activity.primaryInterest, 1, Integer::sum);
            }
        }
        return available;
    }

    Map<InterestType, Integer> scheduledByInterest(TripEntity trip) {
        Map<InterestType, Integer> counts = new EnumMap<>(InterestType.class);
        trip.days.stream().flatMap(day -> day.activities.stream())
            .map(item -> item.activity.primaryInterest)
            .filter(java.util.Objects::nonNull)
            .forEach(type -> counts.merge(type, 1, Integer::sum));
        return counts;
    }

    private Map<InterestType, Integer> quotas(Set<InterestType> selectedTypes, int slots) {
        Map<InterestType, Integer> quotas = new EnumMap<>(InterestType.class);
        if (selectedTypes.isEmpty()) return quotas;
        var ordered = selectedTypes.stream().sorted(Comparator.comparing(Enum::name)).toList();
        int base = slots / ordered.size();
        int remainder = slots % ordered.size();
        for (int index = 0; index < ordered.size(); index++) {
            quotas.put(ordered.get(index), base + (index < remainder ? 1 : 0));
        }
        return quotas;
    }
}
