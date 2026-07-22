package de.travelmate.quality;

import de.travelmate.datasource.ExternalActivityCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class PoiRelationshipService {
    private static final double PARENT_DISTANCE_KM = 1.0;
    private static final Set<String> SUB_POI_TERMS = Set.of(
        "erweiterungsteil", "erweiterung", "teilbereich", "nebengelande", "aussenstelle",
        "annex", "annexe", "anbau", "abschnitt", "bereich", "eingang", "parkplatz",
        "besucherzentrum", "informationszentrum", "extension", "part", "section", "area",
        "wing", "entrance", "parking", "visitor center", "information center", "estensione",
        "ampliamento", "sezione", "annesso", "ampliacion", "seccion"
    );

    @Inject
    PoiQualityEngine qualityEngine;

    public void suppressSubPois(List<ExternalActivityCandidate> candidates) {
        for (ExternalActivityCandidate candidate : candidates) {
            if (candidate.suppressedAsSubPoi) {
                continue;
            }
            Optional<ExternalActivityCandidate> parent = findLikelyParentPoi(candidate, candidates);
            if (shouldSuppressAsMainActivityBecauseSubPoi(candidate, candidates)) {
                candidate.suppressedAsSubPoi = true;
                if (parent.isPresent()) {
                    candidate.suppressionReason = "parent_poi_preferred";
                    candidate.preferredParentName = parent.get().name;
                } else {
                    candidate.suppressionReason = "weak_sub_poi_not_main_activity";
                }
            }
        }
    }

    public boolean isLikelySubPoi(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return SUB_POI_TERMS.stream().anyMatch(term -> containsTerm(name, term));
    }

    public Optional<ExternalActivityCandidate> findLikelyParentPoi(
        ExternalActivityCandidate candidate,
        List<ExternalActivityCandidate> nearbyCandidates
    ) {
        if (!isLikelySubPoi(candidate)) {
            return Optional.empty();
        }
        String childName = normalized(candidate.name);
        String baseName = baseName(childName);
        return nearbyCandidates.stream()
            .filter(parent -> parent != candidate)
            .filter(parent -> !parent.suppressedAsSubPoi)
            .filter(parent -> parent.name != null && !parent.name.isBlank())
            .filter(parent -> isNearby(candidate, parent))
            .filter(parent -> isLikelyParentName(baseName, normalized(parent.name), childName))
            .filter(parent -> hasSimilarCategory(candidate, parent))
            .sorted((first, second) -> Double.compare(parentRank(second), parentRank(first)))
            .findFirst();
    }

    public boolean shouldSuppressAsMainActivityBecauseSubPoi(
        ExternalActivityCandidate candidate,
        List<ExternalActivityCandidate> nearbyCandidates
    ) {
        if (!isLikelySubPoi(candidate)) {
            return false;
        }
        if (engine().hasStrongIndependentNotability(candidate)) {
            return false;
        }
        return findLikelyParentPoi(candidate, nearbyCandidates).isPresent()
            || isWeakInfrastructureSubPoi(candidate)
            || !candidate.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIDATA)
            && !candidate.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIPEDIA);
    }

    private boolean isWeakInfrastructureSubPoi(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return containsTerm(name, "eingang")
            || containsTerm(name, "entrance")
            || containsTerm(name, "parkplatz")
            || containsTerm(name, "parking")
            || containsTerm(name, "besucherzentrum")
            || containsTerm(name, "visitor center")
            || containsTerm(name, "informationszentrum")
            || containsTerm(name, "information center");
    }

    private boolean isLikelyParentName(String baseName, String parentName, String childName) {
        return !parentName.equals(childName)
            && !baseName.isBlank()
            && (parentName.equals(baseName) || baseName.startsWith(parentName + " ") || childName.startsWith(parentName + " "));
    }

    private boolean hasSimilarCategory(ExternalActivityCandidate candidate, ExternalActivityCandidate parent) {
        if (candidate.rawCategories.isEmpty() || parent.rawCategories.isEmpty()) {
            return true;
        }
        return candidate.rawCategories.stream().anyMatch(child ->
            parent.rawCategories.stream().anyMatch(parentCategory ->
                child.equals(parentCategory)
                    || child.startsWith(parentCategory + ".")
                    || parentCategory.startsWith(child + ".")
            )
        );
    }

    private double parentRank(ExternalActivityCandidate parent) {
        double rank = 0;
        if (parent.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIDATA)) rank += 2;
        if (parent.externalRefs.containsKey(de.travelmate.activity.ActivitySource.WIKIPEDIA)) rank += 2;
        if (parent.geometryAreaM2 != null) rank += Math.min(3, Math.log10(Math.max(1, parent.geometryAreaM2)));
        rank += Math.min(2, parent.wikidataSitelinksCount / 50.0);
        return rank;
    }

    private boolean isNearby(ExternalActivityCandidate candidate, ExternalActivityCandidate parent) {
        if (candidate.latitude == null || candidate.longitude == null || parent.latitude == null || parent.longitude == null) {
            return false;
        }
        return distanceInKilometers(candidate.latitude, candidate.longitude, parent.latitude, parent.longitude) <= PARENT_DISTANCE_KM;
    }

    private String baseName(String name) {
        String base = name;
        for (String term : SUB_POI_TERMS) {
            base = base.replace(term, " ");
        }
        return base.replaceAll("\\s+", " ").trim();
    }

    private boolean containsTerm(String name, String term) {
        String normalizedTerm = normalized(term);
        return name.equals(normalizedTerm)
            || name.startsWith(normalizedTerm + " ")
            || name.endsWith(" " + normalizedTerm)
            || name.contains(" " + normalizedTerm + " ");
    }

    private PoiQualityEngine engine() {
        return qualityEngine == null ? new PoiQualityEngine() : qualityEngine;
    }

    private static String normalized(String value) {
        if (value == null) return "";
        String normalizedSharpS = value.trim().toLowerCase(java.util.Locale.ROOT).replace("\u00df", "ss");
        return Normalizer.normalize(normalizedSharpS, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private static double distanceInKilometers(
        double firstLatitude,
        double firstLongitude,
        double secondLatitude,
        double secondLongitude
    ) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
