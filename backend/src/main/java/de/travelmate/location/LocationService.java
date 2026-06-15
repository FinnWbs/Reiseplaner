package de.travelmate.location;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.datasource.ExternalProviderException;
import de.travelmate.datasource.GeoapifyClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class LocationService {
    private static final int LIMIT = 6;
    private final LocationSuggestionMapper mapper = new LocationSuggestionMapper();

    @Inject
    @RestClient
    GeoapifyClient geoapify;

    @ConfigProperty(name = "travelmate.geoapify.api-key")
    Optional<String> configuredApiKey;

    public List<LocationSuggestionDto> autocomplete(String query) {
        String normalizedQuery = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (normalizedQuery.length() < 2) {
            return List.of();
        }
        String apiKey = configuredApiKey.orElse("");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalProviderException(
                "Geoapify ist nicht konfiguriert. Bitte GEOAPIFY_API_KEY setzen.",
                Response.Status.SERVICE_UNAVAILABLE
            );
        }

        try {
            JsonNode response = geoapify.autocomplete(normalizedQuery, "city", LIMIT, "json", "de", apiKey);
            return mapper.fromGeoapify(response);
        } catch (ExternalProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalProviderException("Geoapify Autocomplete konnte nicht erreicht werden.", exception);
        }
    }
}
