package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.travelmate.interest.InterestType;
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
    void rejectsPlaquesLowLevelArtObjectsAndAccessPoints() {
        ExternalActivityCandidate plaque = candidate("Gedenktafel am Rathaus");
        plaque.rawTags.put("memorial", "plaque");
        ExternalActivityCandidate statue = candidate("Reiterstandbild");
        statue.rawCategories.add("tourism.attraction.artwork.statue");
        statue.rawTags.put("artwork_type", "statue");
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

    @Test
    void natureAcceptsOnlyVisitorFriendlyGreenSpaces() {
        ExternalActivityCandidate park = candidate("Tiergarten");
        park.rawCategories.add("leisure.park");
        ExternalActivityCandidate garden = candidate("Botanischer Garten");
        garden.rawCategories.add("leisure.park.garden");
        ExternalActivityCandidate reserve = candidate("Naturschutzgebiet");
        reserve.rawCategories.add("leisure.park.nature_reserve");
        ExternalActivityCandidate nationalPark = candidate("Nationalpark");
        nationalPark.rawCategories.add("national_park");
        ExternalActivityCandidate protectedArea = candidate("Grueneflaeche");
        protectedArea.rawCategories.add("natural.protected_area");
        ExternalActivityCandidate viewpoint = candidate("Aussichtspunkt am Kliff");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("natural", "cliff");

        assertTrue(filtering.isRelevant(park, InterestType.NATURE));
        assertTrue(filtering.isRelevant(garden, InterestType.NATURE));
        assertTrue(filtering.isRelevant(reserve, InterestType.NATURE));
        assertTrue(filtering.isRelevant(nationalPark, InterestType.NATURE));
        assertTrue(filtering.isRelevant(protectedArea, InterestType.NATURE));
        assertTrue(filtering.isRelevant(viewpoint, InterestType.NATURE));
    }

    @Test
    void natureRejectsViewpointWithoutDirectNatureContext() {
        ExternalActivityCandidate viewpoint = candidate("Aussichtspunkt");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("tourism", "viewpoint");

        assertFalse(filtering.isRelevant(viewpoint, InterestType.NATURE));
    }

    @Test
    void natureRejectsRailwayAndIndoorViewpoints() {
        ExternalActivityCandidate railway = candidate("Tokyo Station train tracks Viewpoint 6F");
        railway.rawCategories.add("tourism.attraction.viewpoint");
        railway.rawTags.put("tourism", "viewpoint");
        railway.rawTags.put("railway", "station");
        railway.rawTags.put("level", "6");

        ExternalActivityCandidate indoor = candidate("Rooftop Observation Deck");
        indoor.rawCategories.add("tourism.attraction.viewpoint");
        indoor.rawTags.put("tourism", "viewpoint");
        indoor.rawTags.put("building", "yes");
        indoor.rawTags.put("indoor", "yes");

        assertFalse(filtering.isRelevant(railway, InterestType.NATURE));
        assertFalse(filtering.isRelevant(indoor, InterestType.NATURE));
    }

    @Test
    void natureRejectsWaterFountainsAndInfrastructure() {
        ExternalActivityCandidate fountain = candidate("Neptunbrunnen");
        fountain.rawCategories.add("natural.water");
        fountain.rawCategories.add("tourism.attraction.fountain");
        ExternalActivityCandidate path = candidate("Parkweg");
        path.rawCategories.add("leisure.park");
        path.rawCategories.add("highway.footway");
        ExternalActivityCandidate toilet = candidate("Toilette am Park");
        toilet.rawCategories.add("leisure.park");
        toilet.rawCategories.add("amenity.toilet");
        ExternalActivityCandidate picnicTable = candidate("Picknicktisch");
        picnicTable.rawCategories.add("leisure.park");
        picnicTable.rawCategories.add("leisure.picnic.picnic_table");
        ExternalActivityCandidate rawTree = candidate("Alte Eiche");
        rawTree.rawCategories.add("leisure.park");
        rawTree.rawTags.put("natural", "tree");

        assertFalse(filtering.isRelevant(fountain, InterestType.NATURE));
        assertFalse(filtering.isRelevant(path, InterestType.NATURE));
        assertFalse(filtering.isRelevant(toilet, InterestType.NATURE));
        assertFalse(filtering.isRelevant(picnicTable, InterestType.NATURE));
        assertFalse(filtering.isRelevant(rawTree, InterestType.NATURE));
    }

    private static ExternalActivityCandidate candidate(String name) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = 52.52;
        candidate.longitude = 13.405;
        return candidate;
    }
}
