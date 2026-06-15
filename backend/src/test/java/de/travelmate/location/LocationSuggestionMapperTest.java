package de.travelmate.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocationSuggestionMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocationSuggestionMapper mapper = new LocationSuggestionMapper();

    @Test
    void mapsGeoapifyCityResultsToSuggestions() throws Exception {
        String json = """
            {
              "results": [
                {
                  "place_id": "berlin-de",
                  "city": "Berlin",
                  "country": "Deutschland",
                  "country_code": "de",
                  "state": "Berlin",
                  "formatted": "Berlin, Deutschland",
                  "lat": 52.5170365,
                  "lon": 13.3888599
                }
              ]
            }
            """;

        List<LocationSuggestionDto> suggestions = mapper.fromGeoapify(objectMapper.readTree(json));

        assertEquals(1, suggestions.size());
        LocationSuggestionDto berlin = suggestions.getFirst();
        assertEquals("berlin-de", berlin.id());
        assertEquals("Berlin", berlin.city());
        assertEquals("Deutschland", berlin.country());
        assertEquals("de", berlin.countryCode());
        assertEquals("Berlin, Deutschland", berlin.formatted());
        assertEquals(52.5170365, berlin.latitude());
        assertEquals(13.3888599, berlin.longitude());
        assertEquals("berlin-de", berlin.placeId());
    }

    @Test
    void createsStableFallbackIdWhenPlaceIdIsMissing() throws Exception {
        String json = """
            {
              "results": [
                {
                  "city": "Berlin",
                  "country": "USA",
                  "state": "Maryland",
                  "lat": 38.3226,
                  "lon": -75.2177
                }
              ]
            }
            """;

        LocationSuggestionDto suggestion = mapper.fromGeoapify(objectMapper.readTree(json)).getFirst();

        assertNotNull(suggestion.id());
        assertEquals(16, suggestion.id().length());
        assertEquals("Berlin", suggestion.city());
        assertEquals("USA", suggestion.country());
        assertEquals("Maryland", suggestion.state());
    }
}
