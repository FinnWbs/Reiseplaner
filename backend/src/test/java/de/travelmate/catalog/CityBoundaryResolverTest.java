package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.travelmate.datasource.GeoapifyClient;
import de.travelmate.trip.TripEntity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CityBoundaryResolverTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void selectedTripCoordinatesStayCatalogCenterWhenGeoapifyTextSearchDiffers() {
        CityBoundaryResolver resolver = resolver(
            geoapifyResult(26.5707754, 128.0255901, 127.6, 26.0, 128.3, 26.9)
        );
        TripEntity trip = trip(26.3343738, 127.8056597);

        CityBoundary boundary = resolver.resolve(trip);

        assertEquals(26.3343738, boundary.centerLat());
        assertEquals(127.8056597, boundary.centerLon());
        assertEquals(26.0, boundary.boundingBox().minLat());
        assertEquals(127.6, boundary.boundingBox().minLon());
    }

    @Test
    void geoapifyBoundingBoxIsIgnoredWhenItDoesNotContainSelectedTripCoordinates() {
        CityBoundaryResolver resolver = resolver(
            geoapifyResult(26.5707754, 128.0255901, 128.0, 26.5, 128.1, 26.6)
        );
        TripEntity trip = trip(26.3343738, 127.8056597);

        CityBoundary boundary = resolver.resolve(trip);

        assertEquals(26.3343738, boundary.centerLat());
        assertEquals(127.8056597, boundary.centerLon());
        assertNull(boundary.boundingBox());
    }

    private static CityBoundaryResolver resolver(JsonNode geoapifyResponse) {
        AttractionCatalogSettings settings = new AttractionCatalogSettings();
        settings.cityBoundaryBufferKm = 1.5;
        settings.enclaveExtraBufferKm = 3.0;
        settings.fallbackBoundaryRadiusKm = 12.0;

        CityBoundaryResolver resolver = new CityBoundaryResolver();
        resolver.geoapify = new FakeGeoapifyClient(geoapifyResponse);
        resolver.wikidata = null;
        resolver.settings = settings;
        resolver.configuredApiKey = Optional.of("test-key");
        return resolver;
    }

    private static TripEntity trip(double latitude, double longitude) {
        TripEntity trip = new TripEntity();
        trip.city = "Okinawa";
        trip.country = "Japan";
        trip.countryCode = "jp";
        trip.latitude = latitude;
        trip.longitude = longitude;
        return trip;
    }

    private static JsonNode geoapifyResult(
        double latitude,
        double longitude,
        double lon1,
        double lat1,
        double lon2,
        double lat2
    ) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode location = root.putArray("results").addObject();
        location.put("lat", latitude);
        location.put("lon", longitude);
        ObjectNode bbox = location.putObject("bbox");
        bbox.put("lon1", lon1);
        bbox.put("lat1", lat1);
        bbox.put("lon2", lon2);
        bbox.put("lat2", lat2);
        return root;
    }

    private static class FakeGeoapifyClient implements GeoapifyClient {
        private final JsonNode geocodeResponse;

        FakeGeoapifyClient(JsonNode geocodeResponse) {
            this.geocodeResponse = geocodeResponse;
        }

        @Override
        public JsonNode autocomplete(String text, String type, int limit, String format, String language, String apiKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonNode geocode(String text, String type, int limit, String format, String apiKey) {
            assertEquals("city", type);
            return geocodeResponse;
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
            throw new UnsupportedOperationException();
        }
    }
}
