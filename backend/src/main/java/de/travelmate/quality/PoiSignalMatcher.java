package de.travelmate.quality;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Locale;

@ApplicationScoped
public class PoiSignalMatcher {
    public boolean matches(ExternalActivityCandidate candidate, String... prefixes) {
        for (String category : candidate.rawCategories) {
            String normalizedCategory = category.toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (normalizedCategory.equals(prefix) || normalizedCategory.startsWith(prefix + ".")) return true;
            }
        }
        return false;
    }

    public boolean hasTag(ExternalActivityCandidate candidate, String key) {
        return candidate.rawTags.containsKey(key);
    }

    public boolean hasTagValue(ExternalActivityCandidate candidate, String key, String... acceptedValues) {
        String value = candidate.rawTags.get(key);
        if (value == null) return false;
        String normalizedValue = normalized(value);
        for (String accepted : acceptedValues) {
            if (normalizedValue.equals(normalized(accepted))) return true;
        }
        return false;
    }

    public boolean containsCategoryTerm(ExternalActivityCandidate candidate, String... terms) {
        for (String category : candidate.rawCategories) {
            String normalizedCategory = normalized(category);
            for (String term : terms) {
                if (normalizedCategory.contains(normalized(term))) return true;
            }
        }
        return false;
    }

    public boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    public double sourceConsensusScore(ExternalActivityCandidate candidate) {
        double score = 0;
        if (candidate.externalRefs.containsKey(ActivitySource.GEOAPIFY)) score += 0.20;
        if (candidate.externalRefs.containsKey(ActivitySource.OPEN_STREET_MAP)) score += 0.15;
        if (candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)) score += 0.20;
        if (candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA)) score += 0.20;
        if (candidate.website != null && !candidate.website.isBlank()) score += 0.05;
        return ScoreNormalizer.clamp01(score);
    }

    public String normalized(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
