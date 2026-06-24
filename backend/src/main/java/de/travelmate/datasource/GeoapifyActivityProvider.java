package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivitySource;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GeoapifyActivityProvider implements ActivityProvider {
    private static final Logger LOG = Logger.getLogger(GeoapifyActivityProvider.class);

    @Inject
    @RestClient
    GeoapifyClient client;

    @ConfigProperty(name = "travelmate.geoapify.api-key")
    Optional<String> configuredApiKey;

    @ConfigProperty(name = "travelmate.activity-import.limit", defaultValue = "20")
    int limit;

    @Inject
    GeoapifyCategoryMapper categoryMapper;

    @Inject
    ActivityFilteringService filtering;

    @Inject
    ActivityScoringService scoring;

    @Override
    public List<ExternalActivityCandidate> fetch(String city) {
        return fetch(city, null, null, null, EnumSet.allOf(InterestType.class));
    }

    public List<ExternalActivityCandidate> fetch(
        String locationText,
        String selectedPlaceId,
        Double selectedLatitude,
        Double selectedLongitude,
        Set<InterestType> requestedInterests
    ) {
        String apiKey = configuredApiKey.orElse("");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalProviderException(
                "Geoapify ist nicht konfiguriert. Bitte GEOAPIFY_API_KEY setzen.",
                Response.Status.SERVICE_UNAVAILABLE
            );
        }

        try {
            Double latitude = selectedLatitude;
            Double longitude = selectedLongitude;
            if (latitude == null || longitude == null) {
                JsonNode results = client.geocode(locationText, "city", 1, "json", apiKey).path("results");
                if (!results.isArray() || results.isEmpty()) {
                    throw new ExternalProviderException("Geoapify konnte die Stadt nicht aufloesen.");
                }

                JsonNode location = results.get(0);
                latitude = latitude == null ? location.path("lat").asDouble() : latitude;
                longitude = longitude == null ? location.path("lon").asDouble() : longitude;
            }

            double originLatitude = latitude;
            double originLongitude = longitude;
            String bias = "proximity:" + originLongitude + "," + originLatitude;
            List<ExternalActivityCandidate> candidates = new ArrayList<>();
            Set<InterestType> interests = requestedInterests == null || requestedInterests.isEmpty()
                ? InterestType.primaryTypes()
                : EnumSet.copyOf(requestedInterests);
            Set<String> seen = new HashSet<>();
            for (InterestType interest : interests) {
                List<String> categories = categoryMapper.categoriesFor(interest);
                if (categories.isEmpty()) {
                    continue;
                }
                int radius = interest == InterestType.NATURE ? 25000 : 12000;
                JsonNode features = client.places(
                    String.join(",", categories),
                    "circle:" + originLongitude + "," + originLatitude + "," + radius,
                    bias,
                    Math.min(limit, 20),
                    "de",
                    apiKey
                ).path("features");
                if (!features.isArray()) {
                    continue;
                }
                for (JsonNode feature : features) {
                    ExternalActivityCandidate candidate = candidate(feature.path("properties"), locationText);
                    if (candidate == null || !filtering.isRelevant(candidate) || filtering.isDuplicate(candidate, seen)) {
                        continue;
                    }
                    candidate.primaryInterest = interest;
                    candidate.matchedInterests.add(interest);
                    candidates.add(candidate);
                }
            }
            return candidates.stream()
                .sorted(Comparator.comparingDouble((ExternalActivityCandidate candidate) ->
                    scoring.score(candidate, interests, originLatitude, originLongitude)
                ).reversed())
                .toList();
        } catch (ExternalProviderException exception) {
            throw exception;
        } catch (WebApplicationException exception) {
            int status = exception.getResponse() == null ? 0 : exception.getResponse().getStatus();
            LOG.warnf(
                "Geoapify activity request rejected: city=%s, interests=%s, status=%d",
                locationText,
                requestedInterests,
                status
            );
            throw new ExternalProviderException(
                "Geoapify hat die Aktivitaetsanfrage abgelehnt (HTTP " + status + ").",
                exception
            );
        } catch (RuntimeException exception) {
            LOG.errorf(
                "Geoapify activity request failed: city=%s, interests=%s, exception=%s, reason=%s",
                locationText,
                requestedInterests,
                exception.getClass().getSimpleName(),
                safeFailureReason(exception)
            );
            throw new ExternalProviderException("Geoapify konnte nicht erreicht werden.", exception);
        }
    }

    private static ExternalActivityCandidate candidate(JsonNode properties, String city) {
        String name = text(properties, "name");
        String externalId = text(properties, "place_id");
        if (name == null || externalId == null) {
            return null;
        }
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.source = ActivitySource.GEOAPIFY;
        candidate.externalId = externalId;
        candidate.name = name;
        candidate.city = city;
        properties.path("categories").forEach(category -> candidate.rawCategories.add(category.asText()));
        candidate.rawCategory = candidate.rawCategories.stream().findFirst().orElse(null);
        candidate.address = firstNonBlank(text(properties, "formatted"), text(properties, "address_line2"));
        candidate.website = firstNonBlank(text(properties, "website"), text(properties.path("datasource").path("raw"), "website"));
        candidate.openingHours = firstNonBlank(
            text(properties, "opening_hours"),
            text(properties.path("datasource").path("raw"), "opening_hours")
        );
        candidate.latitude = number(properties, "lat");
        candidate.longitude = number(properties, "lon");
        candidate.externalRefs.put(ActivitySource.GEOAPIFY, externalId);

        JsonNode raw = properties.path("datasource").path("raw");
        String wikidata = firstNonBlank(text(raw, "wikidata"), text(raw, "wikidata_id"));
        if (wikidata != null) {
            candidate.hasWikidata = true;
            candidate.externalRefs.put(ActivitySource.WIKIDATA, wikidata);
        }
        copyRawTag(raw, candidate, "historic");
        copyRawTag(raw, candidate, "memorial");
        copyRawTag(raw, candidate, "memorial:type");
        copyRawTag(raw, candidate, "artwork_type");
        String osmId = text(raw, "osm_id");
        if (osmId != null) {
            candidate.externalRefs.put(ActivitySource.OPEN_STREET_MAP, osmId);
        }
        return candidate;
    }

    private static void copyRawTag(JsonNode raw, ExternalActivityCandidate candidate, String tag) {
        String value = text(raw, tag);
        if (value != null) {
            candidate.rawTags.put(tag, value);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asDouble() : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String safeFailureReason(RuntimeException exception) {
        Throwable cause = exception.getCause();
        String reason = cause == null ? exception.getMessage() : cause.getMessage();
        if (reason == null || reason.isBlank()) {
            return "unbekannt";
        }
        return reason.replaceAll("apiKey=[^&\\s]+", "apiKey=***");
    }

}
