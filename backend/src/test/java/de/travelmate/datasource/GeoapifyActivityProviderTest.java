package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.travelmate.activity.ActivityImportSettings;
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

    @Test
    void multiAreaRequestsUseAreaCenterForFilterAndBiasWithinOneBudget() throws Exception {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        ActivityImportSettings settings = new ActivityImportSettings();
        set(settings, "multiAreaEnabled", true);
        set(settings, "multiAreaMinRawTargetPerArea", 10);
        set(settings, "maxPagesPerInterest", 1);
        MultiAreaImportPlanner planner = new MultiAreaImportPlanner();
        planner.settings = settings;
        planner.radiusResolver = new ImportRadiusResolver();
        planner.radiusResolver.settings = settings;
        planner.reachability = new ReachabilityPolicy();
        planner.reachability.settings = settings;
        GeoapifyActivityProvider provider = provider(client);
        provider.settings = settings;
        provider.areaPlanner = planner;
        ImportDemand demand = demand(60);

        provider.fetch("Berlin", null, 52.52, 13.405, Set.of(InterestType.FOOD), demand);

        assertTrue(client.filters.size() > 1);
        assertEquals(60, client.limits.stream().mapToInt(Integer::intValue).sum());
        assertTrue(client.biases.stream().distinct().count() > 1);
        for (int index = 0; index < client.filters.size(); index++) {
            String filterCenter = client.filters.get(index).substring("circle:".length()).split(",")[0]
                + "," + client.filters.get(index).substring("circle:".length()).split(",")[1];
            assertTrue(client.biases.get(index).contains(filterCenter));
        }
    }

    @Test
    void shoppingImportRunsSeparateMarketplaceRequestWithLongTripTargetAndPlaceFilter() {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        GeoapifyActivityProvider provider = provider(client);

        provider.fetch(
            "Berlin",
            "berlin-place-id",
            52.52,
            13.405,
            Set.of(InterestType.SHOPPING),
            demand(60, InterestType.SHOPPING, 7)
        );

        assertTrue(client.categories.contains("commercial.shopping_mall,commercial.department_store"));
        assertTrue(client.categories.contains("commercial.marketplace"));
        assertEquals(45, marketplaceLimits(client).stream().mapToInt(Integer::intValue).sum());
        assertTrue(marketplaceFilters(client).stream().allMatch(filter -> filter.equals("place:berlin-place-id")));
    }

    @Test
    void shoppingImportUsesShortMarketplaceTargetForThreeDayTrips() {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        GeoapifyActivityProvider provider = provider(client);

        provider.fetch(
            "Berlin",
            "berlin-place-id",
            52.52,
            13.405,
            Set.of(InterestType.SHOPPING),
            demand(60, InterestType.SHOPPING, 3)
        );

        assertEquals(30, marketplaceLimits(client).stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void marketplaceImportFallsBackToAreaCircleWithoutPlaceId() {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        GeoapifyActivityProvider provider = provider(client);

        provider.fetch(
            "Berlin",
            null,
            52.52,
            13.405,
            Set.of(InterestType.SHOPPING),
            demand(60, InterestType.SHOPPING, 7)
        );

        assertTrue(marketplaceFilters(client).stream().allMatch(filter -> filter.startsWith("circle:")));
    }

    @Test
    void multiAreaMarketplaceBudgetIsDistributedWithinTarget() throws Exception {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        ActivityImportSettings settings = new ActivityImportSettings();
        set(settings, "multiAreaEnabled", true);
        set(settings, "multiAreaMinRawTargetPerArea", 10);
        set(settings, "maxPagesPerInterest", 1);
        MultiAreaImportPlanner planner = new MultiAreaImportPlanner();
        planner.settings = settings;
        planner.radiusResolver = new ImportRadiusResolver();
        planner.radiusResolver.settings = settings;
        planner.reachability = new ReachabilityPolicy();
        planner.reachability.settings = settings;
        GeoapifyActivityProvider provider = provider(client);
        provider.settings = settings;
        provider.areaPlanner = planner;

        provider.fetch("Berlin", "berlin-place-id", 52.52, 13.405, Set.of(InterestType.SHOPPING), demand(80, InterestType.SHOPPING, 7));

        List<Integer> marketplaceLimits = marketplaceLimits(client);
        assertTrue(marketplaceLimits.size() > 1);
        assertEquals(45, marketplaceLimits.stream().mapToInt(Integer::intValue).sum());
        assertTrue(marketplaceFilters(client).stream().allMatch(filter -> filter.equals("place:berlin-place-id")));
        assertTrue(marketplaceBiases(client).stream().distinct().count() > 1);
    }

    @Test
    void marketplaceAndStandardShoppingShareDeduplication() {
        FakeGeoapifyClient client = new FakeGeoapifyClient();
        client.duplicateFirstShoppingAndMarketplace = true;
        GeoapifyActivityProvider provider = provider(client);

        List<ExternalActivityCandidate> candidates = provider.fetch(
            "Berlin",
            "berlin-place-id",
            52.52,
            13.405,
            Set.of(InterestType.SHOPPING),
            demand(1, InterestType.SHOPPING, 7)
        );

        long duplicateNameCount = candidates.stream().filter(candidate -> candidate.name.equals("Shared Market")).count();
        assertEquals(1, duplicateNameCount);
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

    private static void set(Object target, String field, Object value) throws Exception {
        java.lang.reflect.Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private static ImportDemand demand(int rawTarget) {
        return demand(rawTarget, InterestType.FOOD, 7);
    }

    private static ImportDemand demand(int rawTarget, InterestType interest, int days) {
        return new ImportDemand(
            "Berlin",
            Set.of(interest),
            days,
            de.travelmate.trip.TripPace.BALANCED,
            3,
            21,
            53,
            152,
            Map.of(interest, rawTarget),
            Map.of(interest, 18),
            3,
            0.55,
            4,
            true,
            true
        );
    }

    private static List<Integer> marketplaceLimits(FakeGeoapifyClient client) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < client.categories.size(); index++) {
            if (client.categories.get(index).equals("commercial.marketplace")) {
                result.add(client.limits.get(index));
            }
        }
        return result;
    }

    private static List<String> marketplaceFilters(FakeGeoapifyClient client) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < client.categories.size(); index++) {
            if (client.categories.get(index).equals("commercial.marketplace")) {
                result.add(client.filters.get(index));
            }
        }
        return result;
    }

    private static List<String> marketplaceBiases(FakeGeoapifyClient client) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < client.categories.size(); index++) {
            if (client.categories.get(index).equals("commercial.marketplace")) {
                result.add(client.biases.get(index));
            }
        }
        return result;
    }

    static class FakeGeoapifyClient implements GeoapifyClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        boolean duplicateFirstShoppingAndMarketplace = false;
        final List<String> categories = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        final List<String> filters = new ArrayList<>();
        final List<String> biases = new ArrayList<>();

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
            this.categories.add(categories);
            offsets.add(offset);
            limits.add(limit);
            filters.add(filter);
            biases.add(bias);
            var root = MAPPER.createObjectNode();
            var features = root.putArray("features");
            for (int index = 0; index < limit; index++) {
                var properties = features.addObject().putObject("properties");
                int id = offset + index;
                boolean marketplace = categories.equals("commercial.marketplace");
                boolean shopping = categories.contains("commercial.shopping_mall") || marketplace;
                if (duplicateFirstShoppingAndMarketplace && shopping && id == 0) {
                    properties.put("name", "Shared Market");
                    properties.put("place_id", "shared-" + categories);
                    properties.put("lat", 52.52);
                    properties.put("lon", 13.405);
                    properties.putArray("categories").add(marketplace ? "commercial.marketplace" : "commercial.shopping_mall");
                    continue;
                }
                properties.put("name", nameFor(categories, id));
                properties.put("place_id", categories.replace(",", "-") + "-" + id);
                properties.put("lat", 52.52 + id * 0.00001);
                properties.put("lon", 13.405);
                properties.putArray("categories").add(categoryFor(categories));
            }
            return root;
        }

        private static String nameFor(String categories, int id) {
            if (categories.equals("commercial.marketplace")) return "Marketplace " + id;
            if (categories.contains("commercial.shopping_mall")) return "Shopping Destination " + id;
            return "Restaurant " + id;
        }

        private static String categoryFor(String categories) {
            if (categories.equals("commercial.marketplace")) return "commercial.marketplace";
            if (categories.contains("commercial.shopping_mall")) return "commercial.shopping_mall";
            return "catering.restaurant";
        }
    }
}
