package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivitySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GeoapifyActivityProvider implements ActivityProvider {
    private static final String CATEGORIES =
        "tourism,entertainment,heritage,leisure,commercial,catering,sport";

    @Inject
    @RestClient
    GeoapifyClient client;

    @ConfigProperty(name = "travelmate.geoapify.api-key")
    Optional<String> configuredApiKey;

    @ConfigProperty(name = "travelmate.activity-import.limit", defaultValue = "40")
    int limit;

    @Override
    public List<ExternalActivityCandidate> fetch(String city) {
        String apiKey = configuredApiKey.orElse("");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalProviderException(
                "Geoapify ist nicht konfiguriert. Bitte GEOAPIFY_API_KEY setzen.",
                Response.Status.SERVICE_UNAVAILABLE
            );
        }

        try {
            JsonNode results = client.geocode(city, "city", 1, "json", apiKey).path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new ExternalProviderException("Geoapify konnte die Stadt nicht aufloesen.");
            }

            JsonNode location = results.get(0);
            double latitude = location.path("lat").asDouble();
            double longitude = location.path("lon").asDouble();
            String placeId = text(location, "place_id");
            String filter = placeId == null
                ? "circle:" + longitude + "," + latitude + ",15000"
                : "place:" + placeId;
            String bias = "proximity:" + longitude + "," + latitude;

            JsonNode features = client.places(CATEGORIES, filter, bias, limit, "de", apiKey).path("features");
            List<ExternalActivityCandidate> candidates = new ArrayList<>();
            if (!features.isArray()) {
                return candidates;
            }

            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                String name = text(properties, "name");
                String externalId = text(properties, "place_id");
                if (name == null || externalId == null) {
                    continue;
                }

                ExternalActivityCandidate candidate = new ExternalActivityCandidate();
                candidate.source = ActivitySource.GEOAPIFY;
                candidate.externalId = externalId;
                candidate.name = name;
                candidate.city = city;
                candidate.rawCategory = firstText(properties.path("categories"));
                candidate.address = firstNonBlank(text(properties, "formatted"), text(properties, "address_line2"));
                candidate.latitude = number(properties, "lat");
                candidate.longitude = number(properties, "lon");
                candidate.externalRefs.put(ActivitySource.GEOAPIFY, externalId);

                JsonNode raw = properties.path("datasource").path("raw");
                String wikidata = firstNonBlank(text(raw, "wikidata"), text(raw, "wikidata_id"));
                if (wikidata != null) {
                    candidate.externalRefs.put(ActivitySource.WIKIDATA, wikidata);
                }
                String osmId = text(raw, "osm_id");
                if (osmId != null) {
                    candidate.externalRefs.put(ActivitySource.OPEN_STREET_MAP, osmId);
                }
                candidates.add(candidate);
            }
            return candidates;
        } catch (ExternalProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalProviderException("Geoapify konnte nicht erreicht werden.", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asDouble() : null;
    }

    private static String firstText(JsonNode values) {
        return values.isArray() && !values.isEmpty() ? values.get(0).asText(null) : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
