package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record MultiAreaImportPlan(
    String city,
    InterestType interest,
    int totalRawTarget,
    List<ImportArea> areas,
    int maxRequestsByArea
) {
    public Map<String, Integer> rawTargetByArea() {
        return areas.stream().collect(Collectors.toMap(ImportArea::id, ImportArea::rawTarget));
    }

    public int distributedRawTarget() {
        return areas.stream().mapToInt(ImportArea::rawTarget).sum();
    }
}
