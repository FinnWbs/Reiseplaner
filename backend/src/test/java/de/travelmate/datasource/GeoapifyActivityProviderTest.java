package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void placesRequestUsesOffsetPaginationUntilRawTargetIsReached() {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        GeoapifyActivityProvider provider = provider(client);
        ImportDemand demand = demand(120);

        List<ExternalActivityCandidate> candidates = provider.fetch(
            "Berlin",
            null,
            52.52,
            13.405,
            Set.of(InterestType.FOOD),
            demand
        );

        assertEquals(120, candidates.size());
        assertEquals(List.of(0, 100), client.offsets);
        assertEquals(List.of(100, 20), client.limits);
        assertTrue(client.limits.stream().allMatch(limit -> limit > 20 || limit == 20));
        assertTrue(client.limits.stream().anyMatch(limit -> limit > 20));
    }

    private static ExternalActivityCandidate candidate(String name, double latitude, double longitude) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = name;
        candidate.latitude = latitude;
        candidate.longitude = longitude;
        return candidate;
    }

    private static GeoapifyActivityProvider provider(FakeGeoapifyClient client) {
        GeoapifyActivityProvider provider = new GeoapifyActivityProvider();
        provider.client = client;
        provider.configuredApiKey = java.util.Optional.of("test-key");
        provider.categoryMapper = new GeoapifyCategoryMapper();
        provider.filtering = new ActivityFilteringService();
        provider.scoring = new ActivityScoringService();
        return provider;
    }

    private static ImportDemand demand(int rawTarget) {
        return new ImportDemand(
            "Berlin",
            Set.of(InterestType.FOOD),
            7,
            de.travelmate.trip.TripPace.BALANCED,
            3,
            21,
            53,
            152,
            Map.of(InterestType.FOOD, rawTarget),
            Map.of(InterestType.FOOD, 18)
        );
    }

    static class FakeGeoapifyClient implements GeoapifyClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        final List<Integer> offsets = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();

        @Override
        public JsonNode autocomplete(String text, String type, int limit, String format, String language, String apiKey) {
            return MAPPER.createObjectNode();
        }

        @Override
        public JsonNode geocode(String text, String type, int limit, String format, String apiKey) {
            return MAPPER.createObjectNode();
        }

        @Override
        public JsonNode places(
            String categories,
            String conditions,
            String filter,
            String bias,
            int limit,
            int offset,
            String language,
            String apiKey
        ) {
            offsets.add(offset);
            limits.add(limit);
            var root = MAPPER.createObjectNode();
            var features = root.putArray("features");
            for (int index = 0; index < limit; index++) {
                var properties = features.addObject().putObject("properties");
                int id = offset + index;
                properties.put("name", "Restaurant " + id);
                properties.put("place_id", "food-" + id);
                properties.put("lat", 52.52 + id * 0.00001);
                properties.put("lon", 13.405);
                properties.putArray("categories").add("catering.restaurant");
            }
            return root;
        }
    }
}
