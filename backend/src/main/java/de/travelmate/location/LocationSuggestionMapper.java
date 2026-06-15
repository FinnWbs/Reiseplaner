package de.travelmate.location;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class LocationSuggestionMapper {
    public List<LocationSuggestionDto> fromGeoapify(JsonNode response) {
        List<LocationSuggestionDto> suggestions = new ArrayList<>();
        JsonNode results = response.path("results");
        if (!results.isArray()) {
            return suggestions;
        }
        for (JsonNode result : results) {
            LocationSuggestionDto suggestion = fromResult(result);
            if (suggestion.city() != null && !suggestion.city().isBlank()) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    private LocationSuggestionDto fromResult(JsonNode result) {
        String city = firstNonBlank(
            text(result, "city"),
            text(result, "name"),
            text(result, "county")
        );
        String country = text(result, "country");
        String countryCode = text(result, "country_code");
        String state = firstNonBlank(text(result, "state"), text(result, "county"));
        String formatted = text(result, "formatted");
        Double latitude = number(result, "lat");
        Double longitude = number(result, "lon");
        String placeId = text(result, "place_id");
        String id = firstNonBlank(placeId, fallbackId(city, country, state, latitude, longitude));
        return new LocationSuggestionDto(
            id,
            city,
            country,
            countryCode,
            state,
            formatted,
            latitude,
            longitude,
            placeId
        );
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asDouble() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String fallbackId(
        String city,
        String country,
        String state,
        Double latitude,
        Double longitude
    ) {
        String raw = String.join("|",
            city == null ? "" : city,
            country == null ? "" : country,
            state == null ? "" : state,
            latitude == null ? "" : latitude.toString(),
            longitude == null ? "" : longitude.toString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfuegbar.", exception);
        }
    }
}
