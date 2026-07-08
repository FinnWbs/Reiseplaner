package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.datasource.CityBoundingBox;
import de.travelmate.datasource.GeoapifyClient;
import de.travelmate.datasource.GeoDistance;
import de.travelmate.datasource.WikidataClient;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CityBoundaryResolver {
    private static final Logger LOG = Logger.getLogger(CityBoundaryResolver.class);

    @Inject
    @RestClient
    GeoapifyClient geoapify;

    @Inject
    @RestClient
    WikidataClient wikidata;

    @Inject
    AttractionCatalogSettings settings;

    @ConfigProperty(name = "travelmate.geoapify.api-key")
    Optional<String> configuredApiKey;

    public CityBoundary resolve(TripEntity trip) {
        GeoapifyCity geoapifyCity = resolveGeoapifyCity(trip);
        WikidataCity wikidataCity = resolveWikidataCity(trip, geoapifyCity);

        Double centerLat = firstNonNull(
            geoapifyCity == null ? null : geoapifyCity.latitude,
            firstNonNull(trip.latitude, wikidataCity == null ? null : wikidataCity.latitude)
        );
        Double centerLon = firstNonNull(
            geoapifyCity == null ? null : geoapifyCity.longitude,
            firstNonNull(trip.longitude, wikidataCity == null ? null : wikidataCity.longitude)
        );
        if (centerLat == null || centerLon == null) {
            return null;
        }

        CityBoundingBox bbox = geoapifyCity == null ? null : geoapifyCity.boundingBox;
        String wikidataId = wikidataCity == null ? null : wikidataCity.wikidataId;
        if (bbox != null) {
            return CityBoundary.fromBoundingBox(
                trip.city,
                wikidataId,
                centerLat,
                centerLon,
                bbox,
                settings.cityBoundaryBufferKm(),
                settings.enclaveExtraBufferKm()
            );
        }
        return CityBoundary.fallback(
            trip.city,
            wikidataId,
            centerLat,
            centerLon,
            settings.fallbackBoundaryRadiusKm(),
            settings.cityBoundaryBufferKm(),
            settings.enclaveExtraBufferKm()
        );
    }

    private GeoapifyCity resolveGeoapifyCity(TripEntity trip) {
        String apiKey = configuredApiKey == null ? "" : configuredApiKey.orElse("");
        if (apiKey.isBlank() || geoapify == null) {
            return null;
        }
        try {
            JsonNode results = geoapify.geocode(locationText(trip), "city", 1, "json", apiKey).path("results");
            if (!results.isArray() || results.isEmpty()) {
                return null;
            }
            JsonNode location = results.get(0);
            Double latitude = number(location, "lat");
            Double longitude = number(location, "lon");
            CityBoundingBox bbox = bbox(location.path("bbox"));
            if (latitude == null || longitude == null) {
                return null;
            }
            return new GeoapifyCity(latitude, longitude, bbox);
        } catch (RuntimeException exception) {
            LOG.warnf(
                "Geoapify city boundary lookup failed for catalog city=%s, reason=%s",
                trip.city,
                safeReason(exception)
            );
            return null;
        }
    }

    private WikidataCity resolveWikidataCity(TripEntity trip, GeoapifyCity geoapifyCity) {
        if (wikidata == null) {
            return null;
        }
        WikidataCity de = searchWikidataCity(trip.city, "de", trip, geoapifyCity);
        return de != null ? de : searchWikidataCity(trip.city, "en", trip, geoapifyCity);
    }

    private WikidataCity searchWikidataCity(String city, String language, TripEntity trip, GeoapifyCity geoapifyCity) {
        try {
            JsonNode search = wikidata.search(
                "wbsearchentities",
                city,
                language,
                "json",
                5,
                "item",
                settings.userAgent()
            ).path("search");
            if (!search.isArray()) {
                return null;
            }
            WikidataCity best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (JsonNode result : search) {
                String id = result.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                Coordinates coordinates = entityCoordinates(id);
                if (coordinates == null) {
                    continue;
                }
                double distance = referenceDistanceKm(trip, geoapifyCity, coordinates);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new WikidataCity(id, coordinates.latitude, coordinates.longitude);
                }
            }
            return best;
        } catch (RuntimeException exception) {
            LOG.warnf("Wikidata city lookup failed for catalog city=%s, reason=%s", city, safeReason(exception));
            return null;
        }
    }

    private Coordinates entityCoordinates(String wikidataId) {
        JsonNode coordinate = wikidata.entity(wikidataId)
            .path("entities")
            .path(wikidataId)
            .path("claims")
            .path("P625")
            .path(0)
            .path("mainsnak")
            .path("datavalue")
            .path("value");
        if (coordinate.isMissingNode()) {
            return null;
        }
        double latitude = coordinate.path("latitude").asDouble(Double.NaN);
        double longitude = coordinate.path("longitude").asDouble(Double.NaN);
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return null;
        }
        return new Coordinates(latitude, longitude);
    }

    private static double referenceDistanceKm(TripEntity trip, GeoapifyCity geoapifyCity, Coordinates coordinates) {
        Double referenceLat = firstNonNull(
            geoapifyCity == null ? null : geoapifyCity.latitude,
            trip.latitude
        );
        Double referenceLon = firstNonNull(
            geoapifyCity == null ? null : geoapifyCity.longitude,
            trip.longitude
        );
        if (referenceLat == null || referenceLon == null) {
            return 0.0;
        }
        return GeoDistance.distanceKm(referenceLat, referenceLon, coordinates.latitude, coordinates.longitude);
    }

    static CityBoundingBox bbox(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() >= 4) {
            return new CityBoundingBox(
                node.get(1).asDouble(),
                node.get(0).asDouble(),
                node.get(3).asDouble(),
                node.get(2).asDouble()
            );
        }
        Double lon1 = number(node, "lon1");
        Double lat1 = number(node, "lat1");
        Double lon2 = number(node, "lon2");
        Double lat2 = number(node, "lat2");
        if (lon1 == null || lat1 == null || lon2 == null || lat2 == null) {
            return null;
        }
        return new CityBoundingBox(
            Math.min(lat1, lat2),
            Math.min(lon1, lon2),
            Math.max(lat1, lat2),
            Math.max(lon1, lon2)
        );
    }

    private static String locationText(TripEntity trip) {
        String city = trip.city == null ? "" : trip.city.trim();
        String country = firstNonBlank(trip.countryCode, trip.country);
        if (country == null || country.isBlank()) {
            return city;
        }
        return city + ", " + country.trim();
    }

    private static Double number(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asDouble() : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static String safeReason(RuntimeException exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return reason.replaceAll("apiKey=[^&\\s]+", "apiKey=***");
    }

    private record GeoapifyCity(double latitude, double longitude, CityBoundingBox boundingBox) {}

    private record WikidataCity(String wikidataId, double latitude, double longitude) {}

    private record Coordinates(double latitude, double longitude) {}
}
