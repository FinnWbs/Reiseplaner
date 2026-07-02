package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import de.travelmate.quality.PoiEligibilityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class ActivityFilteringService {
    private static final Set<String> EXCLUDED_CATEGORY_PREFIXES = Set.of(
        "tourism.information",
        "tourism.sights.memorial",
        "historic.memorial",
        "highway",
        "access",
        "access_limited",
        "no_access"
    );
    private static final Set<String> EXCLUDED_NAME_TERMS = Set.of(
        "plaque", "gedenktafel", "namenstafel"
    );

    @Inject
    PoiEligibilityService eligibility;

    public boolean isRelevant(ExternalActivityCandidate candidate) {
        return isRelevant(candidate, null);
    }

    public boolean isRelevant(ExternalActivityCandidate candidate, InterestType interest) {
        return candidate.name != null
            && !candidate.name.isBlank()
            && candidate.latitude != null
            && candidate.longitude != null
            && candidate.rawCategories.stream().noneMatch(this::isExcludedCategory)
            && candidate.rawTags.entrySet().stream().noneMatch(this::isExcludedTag)
            && !hasExcludedName(candidate.name)
            && (interest != InterestType.NATURE || isRelevantNature(candidate));
    }

    public boolean isDuplicate(ExternalActivityCandidate candidate, Set<String> seen) {
        String key = normalize(candidate.name) + "@" + rounded(candidate.latitude) + "," + rounded(candidate.longitude);
        return !seen.add(key);
    }

    private boolean isExcludedCategory(String category) {
        return EXCLUDED_CATEGORY_PREFIXES.stream().anyMatch(prefix ->
            category.equals(prefix) || category.startsWith(prefix + ".")
        );
    }

    private boolean isExcludedTag(java.util.Map.Entry<String, String> tag) {
        String key = tag.getKey().toLowerCase(Locale.ROOT);
        String value = tag.getValue().toLowerCase(Locale.ROOT);
        return (key.equals("historic") && (value.equals("memorial") || value.equals("monument")))
            || (key.contains("memorial") && (value.equals("plaque") || value.equals("yes")))
            || (key.equals("artwork_type") && Set.of("statue", "sculpture", "mural").contains(value));
    }

    private boolean isRelevantNature(ExternalActivityCandidate candidate) {
        return rules().canBeMainNatureActivity(candidate);
    }

    private static boolean hasExcludedName(String name) {
        String normalized = normalize(name);
        return EXCLUDED_NAME_TERMS.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
    }

    private static String rounded(Double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private PoiEligibilityService rules() {
        return eligibility == null ? new PoiEligibilityService() : eligibility;
    }
}
