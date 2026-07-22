package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.interest.InterestType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttractionCatalogScorerTest {
    @Test
    void pageviewsLiftPopularAttractionAboveSimilarCandidate() {
        WikimediaCatalogCandidate popular = candidate("Popular Landmark", 250_000, 30, 0.9);
        WikimediaCatalogCandidate niche = candidate("Niche Landmark", 2_000, 30, 0.9);

        List<WikimediaCatalogCandidate> ranked = new AttractionCatalogScorer().scoreAndRank(
            List.of(niche, popular),
            15
        );

        assertEquals("Popular Landmark", ranked.get(0).name);
        assertTrue(popular.publicAttractionScore > niche.publicAttractionScore);
    }

    @Test
    void categoryFitKeepsTouristRelevantPlacesCompetitive() {
        WikimediaCatalogCandidate landmark = candidate("Landmark", 50_000, 20, 0.95);
        WikimediaCatalogCandidate weakFit = candidate("Weak Fit", 50_000, 20, 0.2);

        List<WikimediaCatalogCandidate> ranked = new AttractionCatalogScorer().scoreAndRank(
            List.of(weakFit, landmark),
            15
        );

        assertEquals("Landmark", ranked.get(0).name);
    }

    private static WikimediaCatalogCandidate candidate(String name, long pageviews, int sitelinks, double categoryFit) {
        WikimediaCatalogCandidate candidate = new WikimediaCatalogCandidate();
        candidate.name = name;
        candidate.city = "Teststadt";
        candidate.wikidataId = "Q" + Math.abs(name.hashCode());
        candidate.wikipediaProject = "de.wikipedia.org";
        candidate.wikipediaTitle = name;
        candidate.primaryInterest = InterestType.SIGHTSEEING;
        candidate.category = "landmark";
        candidate.latitude = 52.0;
        candidate.longitude = 13.0;
        candidate.hasCoordinates = true;
        candidate.hasImage = true;
        candidate.description = "Beschreibung";
        candidate.pageviews = pageviews;
        candidate.sitelinkCount = sitelinks;
        candidate.categoryFitScore = categoryFit;
        return candidate;
    }
}
