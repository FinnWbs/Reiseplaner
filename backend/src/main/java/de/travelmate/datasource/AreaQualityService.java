package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AreaQualityService {
    public AreaQualityScore score(
        ImportArea area,
        List<ExternalActivityCandidate> candidates,
        Set<InterestType> selectedInterests,
        boolean foodRelevant
    ) {
        List<ExternalActivityCandidate> inArea = candidates.stream()
            .filter(candidate -> candidate.latitude != null && candidate.longitude != null)
            .filter(candidate -> GeoDistance.distanceKm(area.centerLat(), area.centerLon(), candidate.latitude, candidate.longitude)
                <= area.radiusMeters() / 1000.0)
            .toList();
        double candidateQuality = inArea.stream()
            .sorted(Comparator.comparingDouble(this::candidateSignal).reversed())
            .limit(5)
            .mapToDouble(this::candidateSignal)
            .average()
            .orElse(0);
        Set<InterestType> covered = inArea.stream()
            .map(candidate -> candidate.primaryInterest)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        double categoryCoverage = selectedInterests == null || selectedInterests.isEmpty()
            ? 0
            : covered.size() / (double) selectedInterests.size();
        boolean hasAnchor = inArea.stream().anyMatch(candidate -> candidateSignal(candidate) >= 0.65);
        boolean hasFood = !foodRelevant || covered.contains(InterestType.FOOD);
        double reachability = area.reachable() ? 1 : 0;
        double uniqueness = area.areaType() == ImportAreaType.CENTER ? 0.45 : 0.80;
        double spatialContribution = area.distanceFromCityCenterKm() >= 3 ? 1 : 0.35;
        double score = 0.30 * candidateQuality
            + 0.25 * categoryCoverage
            + 0.20 * reachability
            + 0.15 * uniqueness
            + 0.10 * spatialContribution;
        boolean daySuitable = inArea.size() >= 2 && hasAnchor && hasFood && area.reachable();
        return new AreaQualityScore(
            area.id(),
            candidateQuality,
            categoryCoverage,
            reachability,
            uniqueness,
            spatialContribution,
            Math.max(0, Math.min(1, score)),
            daySuitable
        );
    }

    private double candidateSignal(ExternalActivityCandidate candidate) {
        double signal = 0.25;
        if (candidate.hasWikidata || candidate.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIDATA)) {
            signal += 0.20;
        }
        if (candidate.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIPEDIA)) {
            signal += 0.20;
        }
        if (candidate.website != null && !candidate.website.isBlank()) {
            signal += 0.15;
        }
        if (candidate.openingHours != null && !candidate.openingHours.isBlank()) {
            signal += 0.10;
        }
        if (candidate.geometryAreaM2 != null && candidate.geometryAreaM2 > 50_000) {
            signal += 0.10;
        }
        return Math.max(0, Math.min(1, signal));
    }
}
