package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivitySource;
import de.travelmate.interest.InterestType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AreaQualityServiceTest {
    @Test
    void areaQualityIsPostImportAndRequiresAnchorAndFoodWhenRelevant() {
        AreaQualityService service = new AreaQualityService();
        ImportArea area = new ImportArea("east", "East", 52.52, 13.50, 6000, 0.3, 30, ImportAreaType.EAST, 6, true);
        ExternalActivityCandidate anchor = candidate("Museum", InterestType.CULTURE, 52.52, 13.50);
        anchor.externalRefs.put(ActivitySource.WIKIDATA, "Q1");
        anchor.externalRefs.put(ActivitySource.WIKIPEDIA, "Museum");
        anchor.website = "https://example.test";
        ExternalActivityCandidate food = candidate("Restaurant", InterestType.FOOD, 52.521, 13.501);
        food.openingHours = "Mo-Su 10:00-22:00";

        AreaQualityScore suitable = service.score(
            area,
            List.of(anchor, food),
            Set.of(InterestType.CULTURE, InterestType.FOOD),
            true
        );
        AreaQualityScore missingFood = service.score(
            area,
            List.of(anchor),
            Set.of(InterestType.CULTURE, InterestType.FOOD),
            true
        );

        assertTrue(suitable.daySuitable());
        assertFalse(missingFood.daySuitable());
    }

    private static ExternalActivityCandidate candidate(String name, InterestType interest, double lat, double lon) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.primaryInterest = interest;
        candidate.latitude = lat;
        candidate.longitude = lon;
        return candidate;
    }
}
