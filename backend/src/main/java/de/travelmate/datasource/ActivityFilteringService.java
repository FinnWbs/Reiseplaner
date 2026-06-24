package de.travelmate.datasource;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class ActivityFilteringService {
    private static final Set<String> EXCLUDED_CATEGORY_PREFIXES = Set.of(
        "tourism.information",
        "tourism.attraction.artwork",
        "tourism.attraction.fountain",
        "tourism.sights.memorial",
        "heritage",
        "historic",
        "building",
        "highway",
        "access",
        "access_limited",
        "no_access"
    );
    private static final Set<String> EXCLUDED_NAME_TERMS = Set.of(
        "gedenk", "denkmal", "memorial", "plaque", "tafel", "statue", "sculpture", "skulptur", "mural"
    );

    public boolean isRelevant(ExternalActivityCandidate candidate) {
        return candidate.name != null
            && !candidate.name.isBlank()
            && candidate.latitude != null
            && candidate.longitude != null
            && candidate.rawCategories.stream().noneMatch(this::isExcludedCategory)
            && candidate.rawTags.entrySet().stream().noneMatch(this::isExcludedTag)
            && !hasExcludedName(candidate.name);
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
}
