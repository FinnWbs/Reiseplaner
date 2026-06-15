package de.travelmate.activity;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class ActivityCategoryMapper {
    public CategoryMapping map(String rawCategory, String name) {
        String value = ((rawCategory == null ? "" : rawCategory) + " " + (name == null ? "" : name))
            .toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new LinkedHashMap<>();

        add(scores, value, "Kultur", 10, "museum", "gallery", "art", "theatre", "cinema", "culture");
        add(scores, value, "Geschichte", 10, "heritage", "historic", "monument", "memorial", "castle");
        add(scores, value, "Natur", 10, "park", "natural", "garden", "beach", "viewpoint", "forest");
        add(scores, value, "Food", 10, "catering", "restaurant", "cafe", "food", "market");
        add(scores, value, "Shopping", 10, "commercial", "shop", "mall", "shopping");
        add(scores, value, "Nightlife", 10, "nightclub", "club", "bar", "pub");
        add(scores, value, "Sport", 10, "sport", "stadium", "fitness");

        if (scores.isEmpty()) {
            scores.put("Kultur", 5);
        }
        String dominant = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow()
            .getKey();
        return new CategoryMapping(dominant, Map.copyOf(scores));
    }

    private static void add(
        Map<String, Integer> scores,
        String value,
        String interest,
        int score,
        String... keywords
    ) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                scores.put(interest, score);
                return;
            }
        }
    }

    public record CategoryMapping(String dominantCategory, Map<String, Integer> interestScores) {}
}
