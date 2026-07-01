package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeoapifyActivityProviderTest {
    @Test
    void nearbyShopDensityCountsRetailPoisWithinTwoHundredFiftyMeters() {
        ExternalActivityCandidate street = candidate("Shopping Street", 52.5200, 13.4050);
        street.rawCategories.add("commercial");
        ExternalActivityCandidate firstShop = candidate("Shop A", 52.5205, 13.4050);
        firstShop.rawCategories.add("commercial.clothing");
        ExternalActivityCandidate secondShop = candidate("Shop B", 52.5210, 13.4050);
        secondShop.rawTags.put("shop", "shoes");
        ExternalActivityCandidate farShop = candidate("Far Shop", 52.5300, 13.4050);
        farShop.rawCategories.add("commercial.clothing");

        GeoapifyActivityProvider.populateNearbyShopDensity(List.of(street, firstShop, secondShop, farShop));

        assertEquals(2, street.nearbyShopDensity);
        assertEquals(2, firstShop.nearbyShopDensity);
        assertEquals(2, secondShop.nearbyShopDensity);
        assertEquals(0, farShop.nearbyShopDensity);
    }

    private static ExternalActivityCandidate candidate(String name, double latitude, double longitude) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = latitude;
        candidate.longitude = longitude;
        return candidate;
    }
}
