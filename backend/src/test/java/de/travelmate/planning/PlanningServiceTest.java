package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityInterestEntity;
import de.travelmate.interest.InterestEntity;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PlanningServiceTest {
    @Test
    void scoreUsesInterestMatchAsDominantFactor() {
        PlanningService service = new PlanningService();
        InterestEntity culture = interest(1L, "Kultur");

        ActivityEntity strongMatch = activity(4.0, 0.7);
        strongMatch.interestScores.add(mapping(strongMatch, culture, 10));

        ActivityEntity weakNoMatch = activity(5.0, 1.0);

        double matched = service.score(strongMatch, Set.of(1L)).totalScore();
        double unmatched = service.score(weakNoMatch, Set.of(1L)).totalScore();

        assertTrue(matched > unmatched);
    }

    @Test
    void activityWithoutSelectedInterestCanScoreZero() {
        PlanningService service = new PlanningService();
        ActivityEntity activity = activity(null, 0);

        assertEquals(0, service.score(activity, Set.of(99L)).totalScore());
    }

    private ActivityEntity activity(Double rating, double quality) {
        ActivityEntity activity = new ActivityEntity();
        activity.rating = rating;
        activity.dataQualityScore = quality;
        return activity;
    }

    private InterestEntity interest(Long id, String name) {
        InterestEntity interest = new InterestEntity();
        interest.id = id;
        interest.name = name;
        return interest;
    }

    private ActivityInterestEntity mapping(ActivityEntity activity, InterestEntity interest, int score) {
        ActivityInterestEntity mapping = new ActivityInterestEntity();
        mapping.activity = activity;
        mapping.interest = interest;
        mapping.score = score;
        return mapping;
    }
}
