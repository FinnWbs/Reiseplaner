package de.travelmate.activity;

import de.travelmate.datasource.GeoapifyCategoryMapper;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ActivityCategoryMapper {
    private final GeoapifyCategoryMapper geoapifyCategories = new GeoapifyCategoryMapper();

    public CategoryMapping map(InterestType primaryInterest, Set<String> rawCategories) {
        Map<InterestType, Integer> scores = geoapifyCategories.scoreInterest(primaryInterest);
        if (scores.isEmpty()) {
            scores = geoapifyCategories.scoreInterests(rawCategories);
        }
        if (scores.isEmpty()) {
            scores = Map.of(InterestType.SIGHTSEEING, 5);
        }
        InterestType dominant = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow()
            .getKey();
        return new CategoryMapping(dominant.displayName(), Map.copyOf(scores));
    }

    public CategoryMapping map(Set<String> rawCategories) {
        return map(null, rawCategories);
    }

    public record CategoryMapping(String dominantCategory, Map<InterestType, Integer> interestScores) {}
}
