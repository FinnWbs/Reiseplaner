package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class ActivityTimeRules {
    private static final TimeProfile NIGHTLIFE = new TimeProfile(1080, 1440, 180, 1200);
    private static final TimeProfile FOOD = new TimeProfile(660, 1380, 90, 720);
    private static final TimeProfile NATURE = new TimeProfile(360, 1200, 120, 600);
    private static final TimeProfile CULTURE = new TimeProfile(540, 1200, 120, 600);
    private static final TimeProfile DAYTIME = new TimeProfile(540, 1200, 90, 600);

    public static TimeProfile profile(ActivityEntity activity) {
        Set<String> terms = terms(
            nullToEmpty(activity.category) + " "
                + nullToEmpty(activity.subcategory) + " "
                + nullToEmpty(activity.name)
        );

        if (containsAny(terms, "nightlife", "nightclub", "club", "bar", "pub")) {
            return NIGHTLIFE;
        }
        if (containsAny(terms, "food", "restaurant", "cafe", "catering", "market")) {
            return FOOD;
        }
        if (containsAny(terms, "natur", "nature", "park", "garden", "forest", "beach", "viewpoint")) {
            return NATURE;
        }
        if (containsAny(terms, "kultur", "culture", "museum", "gallery", "shopping")) {
            return CULTURE;
        }
        if (containsAny(terms, "geschichte", "heritage", "historic", "monument", "castle",
            "shop", "mall", "sport", "stadium")) {
            return DAYTIME;
        }
        return DAYTIME;
    }

    public static boolean fitsAt(ActivityEntity activity, int start, int duration) {
        TimeProfile profile = profile(activity);
        return start >= profile.earliestStart() && start + duration <= profile.latestEnd();
    }

    private static Set<String> terms(String value) {
        return new HashSet<>(Arrays.asList(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
    }

    private static boolean containsAny(Set<String> terms, String... needles) {
        for (String needle : needles) {
            if (terms.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record TimeProfile(int earliestStart, int latestEnd, int durationMinutes, int preferredStart) {}
}
