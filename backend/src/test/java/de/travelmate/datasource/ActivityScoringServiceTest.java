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

    @Test
    void natureQualityBonusPrefersGardenOverGenericPark() {
        ExternalActivityCandidate garden = candidate(52.5201, 13.4051);
        garden.primaryInterest = InterestType.NATURE;
        garden.rawCategories.add("leisure.park.garden");
        ExternalActivityCandidate park = candidate(52.5201, 13.4051);
        park.primaryInterest = InterestType.NATURE;
        park.rawCategories.add("leisure.park");

        double gardenScore = scoring.score(garden, Set.of(InterestType.NATURE), 52.52, 13.405);
        double parkScore = scoring.score(park, Set.of(InterestType.NATURE), 52.52, 13.405);

        assertTrue(gardenScore > parkScore);
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
