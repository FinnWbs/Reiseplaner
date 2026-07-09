package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.travelmate.interest.InterestType;
import org.junit.jupiter.api.Test;

class GeoapifyCategoryMapperTest {
    private final GeoapifyCategoryMapper mapper = new GeoapifyCategoryMapper();

    @Test
    void sightseeingImportsBroadCandidatesForQualityFiltering() {
        assertTrue(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.sights"));
        assertTrue(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.sights.castle"));
        assertTrue(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.attraction.viewpoint"));
        assertTrue(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.attraction.fountain"));
        assertTrue(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.attraction.artwork"));
    }

    @Test
    void natureImportsVisitorFriendlyGreenAndProminentOutdoorCategories() {
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("leisure.park"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("leisure.park.garden"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("leisure.park.nature_reserve"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("natural.protected_area"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("natural.forest"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("beach"));
        assertTrue(mapper.categoriesFor(InterestType.NATURE).contains("national_park"));

        assertFalse(mapper.categoriesFor(InterestType.NATURE).contains("tourism.attraction.viewpoint"));
        assertFalse(mapper.categoriesFor(InterestType.NATURE).contains("natural.water"));
        assertFalse(mapper.categoriesFor(InterestType.NATURE).contains("natural.beach"));
        assertFalse(mapper.categoriesFor(InterestType.NATURE).contains("natural.waterfall"));
        assertFalse(mapper.categoriesFor(InterestType.NATURE).contains("tourism.sights.viewpoint"));
    }

    @Test
    void shoppingImportsDestinationsNotSingleStores() {
        assertTrue(mapper.categoriesFor(InterestType.SHOPPING).contains("commercial.shopping_mall"));
        assertTrue(mapper.categoriesFor(InterestType.SHOPPING).contains("commercial.department_store"));

        assertFalse(mapper.categoriesFor(InterestType.SHOPPING).contains("commercial.marketplace"));
        assertFalse(mapper.categoriesFor(InterestType.SHOPPING).contains("commercial.clothing"));
        assertFalse(mapper.categoriesFor(InterestType.SHOPPING).contains("commercial.gift_and_souvenir"));
    }
}
