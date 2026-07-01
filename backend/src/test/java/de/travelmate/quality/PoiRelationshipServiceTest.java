package de.travelmate.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoiRelationshipServiceTest {
    private final PoiRelationshipService relationships = new PoiRelationshipService();

    @Test
    void botanischerGartenErweiterungsteilFindsParentAndIsSuppressed() {
        ExternalActivityCandidate parent = candidate("Botanischer Garten", 52.5200, 13.4050);
        parent.rawCategories.add("leisure.park.garden");
        parent.geometryAreaM2 = 400_000d;
        parent.externalRefs.put(ActivitySource.WIKIDATA, "Q1");
        ExternalActivityCandidate child = candidate("Botanischer Garten Erweiterungsteil", 52.5205, 13.4051);
        child.rawCategories.add("leisure.park.garden");

        List<ExternalActivityCandidate> candidates = List.of(parent, child);

        assertTrue(relationships.isLikelySubPoi(child));
        assertEquals(parent, relationships.findLikelyParentPoi(child, candidates).orElseThrow());
        assertTrue(relationships.shouldSuppressAsMainActivityBecauseSubPoi(child, candidates));

        relationships.suppressSubPois(candidates);

        assertTrue(child.suppressedAsSubPoi);
        assertEquals("parent_poi_preferred", child.suppressionReason);
        assertEquals("Botanischer Garten", child.preferredParentName);
        assertFalse(parent.suppressedAsSubPoi);
    }

    @Test
    void weakAnnexWithoutParentIsSuppressed() {
        ExternalActivityCandidate annex = candidate("Garden Annex", 52.5200, 13.4050);
        annex.rawCategories.add("leisure.park.garden");

        relationships.suppressSubPois(List.of(annex));

        assertTrue(annex.suppressedAsSubPoi);
        assertEquals("weak_sub_poi_not_main_activity", annex.suppressionReason);
    }

    @Test
    void independentlyRelevantAnnexCanRemainMainActivity() {
        ExternalActivityCandidate annex = candidate("Museum Wing", 52.5200, 13.4050);
        annex.rawCategories.add("entertainment.museum");
        annex.externalRefs.put(ActivitySource.WIKIDATA, "Q2");
        annex.externalRefs.put(ActivitySource.WIKIPEDIA, "Museum Wing");
        annex.wikidataSitelinksCount = 150;
        annex.wikipediaPageviews365d = 700_000;

        assertTrue(relationships.isLikelySubPoi(annex));
        assertFalse(relationships.shouldSuppressAsMainActivityBecauseSubPoi(annex, List.of(annex)));
    }

    @Test
    void entranceParkingAndWeakVisitorCenterAreSuppressed() {
        ExternalActivityCandidate entrance = candidate("Botanical Garden Entrance", 52.5200, 13.4050);
        entrance.rawCategories.add("leisure.park.garden");
        ExternalActivityCandidate parking = candidate("Botanical Garden Parking", 52.5200, 13.4050);
        parking.rawCategories.add("parking");
        ExternalActivityCandidate visitorCenter = candidate("Botanical Garden Visitor Center", 52.5200, 13.4050);
        visitorCenter.rawCategories.add("tourism.information");

        relationships.suppressSubPois(List.of(entrance, parking, visitorCenter));

        assertTrue(entrance.suppressedAsSubPoi);
        assertTrue(parking.suppressedAsSubPoi);
        assertTrue(visitorCenter.suppressedAsSubPoi);
    }

    @Test
    void doesNotMergeDifferentPoisWithoutSubIndicator() {
        ExternalActivityCandidate castleMuseum = candidate("Schlossmuseum", 52.5200, 13.4050);
        ExternalActivityCandidate castleArcade = candidate("Schloss-Arkaden", 52.5201, 13.4051);
        ExternalActivityCandidate botanicalGarden = candidate("Botanischer Garten", 52.5200, 13.4050);
        ExternalActivityCandidate botanicalMuseum = candidate("Botanisches Museum", 52.5201, 13.4051);
        ExternalActivityCandidate oldMarket = candidate("Alter Markt", 52.5200, 13.4050);
        ExternalActivityCandidate marketHall = candidate("Markthalle", 52.5201, 13.4051);

        List<ExternalActivityCandidate> candidates = List.of(
            castleMuseum, castleArcade, botanicalGarden, botanicalMuseum, oldMarket, marketHall
        );

        for (ExternalActivityCandidate candidate : candidates) {
            assertFalse(relationships.isLikelySubPoi(candidate));
            assertTrue(relationships.findLikelyParentPoi(candidate, candidates).isEmpty());
        }
    }

    private static ExternalActivityCandidate candidate(String name, double latitude, double longitude) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = latitude;
        candidate.longitude = longitude;
        return candidate;
    }
}
