package de.travelmate.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import org.junit.jupiter.api.Test;

class PoiEligibilityServiceTest {
    private final PoiEligibilityService eligibility = new PoiEligibilityService();
    private final PoiViewpointClassifier viewpoints = new PoiViewpointClassifier();

    @Test
    void natureEligibilityRejectsParkInfrastructure() {
        ExternalActivityCandidate toilet = candidate("Toilette am Park");
        toilet.rawCategories.add("leisure.park");
        toilet.rawCategories.add("amenity.toilet");
        ExternalActivityCandidate tree = candidate("Alte Eiche");
        tree.rawCategories.add("leisure.park");
        tree.rawTags.put("natural", "tree");

        assertFalse(eligibility.canBeMainNatureActivity(toilet));
        assertFalse(eligibility.canBeMainNatureActivity(tree));
    }

    @Test
    void natureEligibilityKeepsRealParksAndGardens() {
        ExternalActivityCandidate park = candidate("Tiergarten");
        park.rawCategories.add("leisure.park");
        ExternalActivityCandidate garden = candidate("Botanischer Garten");
        garden.rawCategories.add("leisure.park.garden");
        garden.rawTags.put("garden:type", "botanical");

        assertTrue(eligibility.canBeMainNatureActivity(park));
        assertTrue(eligibility.canBeMainNatureActivity(garden));
    }

    @Test
    void shoppingEligibilitySeparatesDestinationsFromSingleStores() {
        ExternalActivityCandidate mall = candidate("Shopping Center");
        mall.rawCategories.add("commercial.shopping_mall");
        ExternalActivityCandidate store = candidate("Xi Milano");
        store.rawCategories.add("commercial.clothing");
        store.rawTags.put("shop", "clothes");

        assertTrue(eligibility.isShoppingDestination(mall));
        assertTrue(eligibility.canBeMainShoppingActivity(mall, 0, 0));
        assertTrue(eligibility.isSingleRetailStore(store));
        assertFalse(eligibility.canBeMainShoppingActivity(store, 0.5, 0.5));
    }

    @Test
    void notableSingleStoreCanBeShoppingActivity() {
        ExternalActivityCandidate store = candidate("Relevant Flagship Store");
        store.rawCategories.add("commercial.clothing");
        store.rawTags.put("shop", "clothes");
        store.externalRefs.put(ActivitySource.WIKIDATA, "Q1");
        store.externalRefs.put(ActivitySource.WIKIPEDIA, "Relevant Flagship Store");

        assertTrue(eligibility.canBeMainShoppingActivity(store, 0.75, 0.2));
    }

    @Test
    void viewpointClassifierRecognizesInfrastructureBeforeNature() {
        ExternalActivityCandidate viewpoint = candidate("Tokyo Station train tracks Viewpoint 6F");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("tourism", "viewpoint");
        viewpoint.rawTags.put("railway", "station");
        viewpoint.rawTags.put("level", "6");

        assertTrue(viewpoints.isViewpoint(viewpoint));
        assertEquals(ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE, viewpoints.subtype(viewpoint));
        assertFalse(eligibility.canBeMainNatureActivity(viewpoint));
        assertFalse(eligibility.canBeMainSightseeingActivity(viewpoint, 0.4, 0.4));
    }

    private static ExternalActivityCandidate candidate(String name) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = 52.52;
        candidate.longitude = 13.405;
        return candidate;
    }
}
