package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import de.travelmate.interest.InterestType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityScoringServiceTest {
    private final ActivityScoringService scoring = new ActivityScoringService();

    @Test
    void prefersNearbyActivityThatMatchesRequestedInterest() {
        ExternalActivityCandidate matching = candidate(52.5201, 13.4051);
        matching.matchedInterests.add(InterestType.CULTURE);
        matching.primaryInterest = InterestType.CULTURE;
        ExternalActivityCandidate distant = candidate(52.62, 13.55);

        double matchingScore = scoring.score(matching, Set.of(InterestType.CULTURE), 52.52, 13.405);
        double distantScore = scoring.score(distant, Set.of(InterestType.CULTURE), 52.52, 13.405);

        assertTrue(matchingScore > distantScore);
    }

    private static ExternalActivityCandidate candidate(double latitude, double longitude) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = "Testort";
        candidate.latitude = latitude;
        candidate.longitude = longitude;
        candidate.address = "Adresse";
        return candidate;
    }
}
