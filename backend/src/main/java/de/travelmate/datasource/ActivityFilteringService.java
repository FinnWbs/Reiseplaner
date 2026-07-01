package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;
import java.util.HashSet;
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
    private static final Set<String> NATURE_CATEGORY_PREFIXES = Set.of(
        "leisure.park",
        "leisure.park.garden",
        "leisure.park.nature_reserve",
        "national_park",
        "natural.protected_area",
        "natural.forest",
        "beach"
    );
    private static final Set<String> NATURE_EXCLUDED_CATEGORY_PREFIXES = Set.of(
        "natural.water",
        "tourism.attraction.fountain",
        "tourism.attraction.artwork",
        "tourism.sights.memorial",
        "historic.memorial",
        "cemetery",
        "graveyard",
        "burial",
        "crematorium",
        "funeral",
        "commercial",
        "shop",
        "catering",
        "entertainment.museum",
        "entertainment.culture.gallery",
        "waterway",
        "highway",
        "emergency",
        "parking",
        "amenity.toilet",
        "leisure.playground",
        "leisure.picnic",
        "leisure.golf_course",
        "sport.golf",
        "railway",
        "public_transport",
        "aeroway",
        "access",
        "access_limited",
        "no_access"
    );
    private static final Set<String> NATURE_EXCLUDED_TAG_VALUES = Set.of(
        "water", "fountain", "spring", "tree", "picnic_table", "playground", "toilets",
        "parking", "entrance", "path", "footway", "track", "restaurant", "cafe", "bar",
        "pub", "museum", "gallery", "artwork", "memorial", "monument", "retail",
        "commercial", "office", "cemetery", "graveyard", "grave yard", "burial",
        "crematorium", "funeral", "golf course", "private", "station", "platform",
        "railway", "train", "airport", "terminal", "indoor"
    );

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
        boolean acceptedNatureType = candidate.rawCategories.stream().anyMatch(category ->
            matchesPrefix(category, NATURE_CATEGORY_PREFIXES)
        ) || hasAcceptedNatureTag(candidate);
        boolean excludedNatureType = candidate.rawCategories.stream().anyMatch(category ->
            matchesPrefix(category, NATURE_EXCLUDED_CATEGORY_PREFIXES)
        );
        boolean excludedRawTag = candidate.rawTags.entrySet().stream().anyMatch(tag -> {
            String key = normalize(tag.getKey());
            String value = normalize(tag.getValue());
            return NATURE_EXCLUDED_TAG_VALUES.contains(key)
                || NATURE_EXCLUDED_TAG_VALUES.contains(value)
                || key.equals("shop")
                || key.equals("man made")
                || key.equals("office")
                || key.equals("cemetery")
                || key.equals("grave")
                || key.equals("grave yard")
                || key.equals("funeral")
                || key.equals("railway")
                || key.equals("public transport")
                || key.equals("aeroway")
                || key.equals("platform")
                || key.equals("subway")
                || key.equals("rail")
                || key.equals("terminal")
                || key.equals("level")
                || key.equals("addr floor")
                || (key.equals("landuse") && value.equals("cemetery"))
                || (key.equals("amenity") && (value.equals("grave yard") || value.equals("crematorium")))
                || (key.equals("amenity") && value.equals("parking"))
                || (key.equals("historic") && value.equals("cemetery"))
                || (key.equals("leisure") && value.equals("golf course"))
                || (key.equals("access") && value.equals("private"))
                || (key.equals("building") && !value.isBlank())
                || (key.equals("indoor") && value.equals("yes"));
        });
        if (isViewpoint(candidate) && (!hasAcceptedNatureTag(candidate) || excludedRawTag || excludedNatureType)) {
            return false;
        }
        return acceptedNatureType && !excludedNatureType && !excludedRawTag;
    }

    private boolean hasAcceptedNatureTag(ExternalActivityCandidate candidate) {
        return candidate.rawTags.entrySet().stream().anyMatch(tag -> {
            String key = normalize(tag.getKey());
            String value = normalize(tag.getValue());
            return (key.equals("leisure") && Set.of("park", "garden", "nature reserve").contains(value))
                || (key.equals("natural") && Set.of(
                    "peak", "cliff", "hill", "mountain", "mountain range", "water", "wood", "forest", "beach", "waterfall"
                ).contains(value))
                || (key.equals("boundary") && value.equals("national park"))
                || key.equals("protected area")
                || (key.equals("route") && value.equals("hiking"))
                || (key.equals("garden type") && Set.of("botanical", "botanic").contains(value))
                || (key.equals("garden") && value.equals("botanical"))
                || (key.equals("botanical") && value.equals("yes"));
        });
    }

    private boolean isViewpoint(ExternalActivityCandidate candidate) {
        String name = normalize(candidate.name == null ? "" : candidate.name);
        return candidate.rawCategories.stream().anyMatch(category ->
            category.equals("tourism.attraction.viewpoint") || category.startsWith("tourism.attraction.viewpoint.")
                || category.equals("tourism.sights.viewpoint") || category.startsWith("tourism.sights.viewpoint.")
        )
            || "viewpoint".equalsIgnoreCase(candidate.rawTags.get("tourism"))
            || name.contains("viewpoint")
            || name.contains("aussichtspunkt")
            || name.contains("observation deck")
            || name.contains("viewing platform");
    }

    private static boolean matchesPrefix(String category, Set<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> category.equals(prefix) || category.startsWith(prefix + "."));
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
