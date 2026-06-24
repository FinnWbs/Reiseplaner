package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ActivityFilteringServiceTest {
    private final ActivityFilteringService filtering = new ActivityFilteringService();

    @Test
    void rejectsMemorialsAndUnnamedPlaces() {
        ExternalActivityCandidate memorial = candidate("Gedenkstein");
        memorial.rawCategories.add("tourism.sights.memorial.milestone");

        ExternalActivityCandidate unnamed = candidate(" ");

        assertFalse(filtering.isRelevant(memorial));
        assertFalse(filtering.isRelevant(unnamed));
    }

    @Test
    void rejectsPlaquesStatuesAndAccessPoints() {
        ExternalActivityCandidate plaque = candidate("Gedenktafel am Rathaus");
        plaque.rawTags.put("memorial", "plaque");
        ExternalActivityCandidate statue = candidate("Reiterstandbild");
        statue.rawCategories.add("tourism.attraction.artwork.statue");
        ExternalActivityCandidate accessPoint = candidate("Zugang Nord");
        accessPoint.rawCategories.add("access");

        assertFalse(filtering.isRelevant(plaque));
        assertFalse(filtering.isRelevant(statue));
        assertFalse(filtering.isRelevant(accessPoint));
    }

    @Test
    void detectsDuplicateNameAtSameLocation() {
        ExternalActivityCandidate first = candidate("Museum Insel");
        ExternalActivityCandidate duplicate = candidate("Museum Insel");
        HashSet<String> seen = new HashSet<>();

        assertFalse(filtering.isDuplicate(first, seen));
        assertTrue(filtering.isDuplicate(duplicate, seen));
    }

    private static ExternalActivityCandidate candidate(String name) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = 52.52;
        candidate.longitude = 13.405;
        return candidate;
    }
}
