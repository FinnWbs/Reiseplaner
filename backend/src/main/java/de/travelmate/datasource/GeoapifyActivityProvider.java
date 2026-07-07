package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.activity.ActivitySource;
import de.travelmate.activity.ImportDemand;
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
import java.util.Locale;
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

    @Inject
    ActivityImportSettings settings;

    @Inject
    GeoapifyCategoryMapper categoryMapper;

    @Inject
    ActivityFilteringService filtering;

    @Inject
    ActivityScoringService scoring;

    @Inject
    MultiAreaImportPlanner areaPlanner;

    @Inject
    AreaQualityService areaQuality;

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
        return fetch(locationText, selectedPlaceId, selectedLatitude, selectedLongitude, requestedInterests, null);
    }

    public List<ExternalActivityCandidate> fetch(
        String locationText,
        String selectedPlaceId,
        Double selectedLatitude,
        Double selectedLongitude,
        Set<InterestType> requestedInterests,
        ImportDemand demand
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
            CityBoundingBox bbox = null;
            if (latitude == null || longitude == null) {
                JsonNode results = client.geocode(locationText, "city", 1, "json", apiKey).path("results");
                if (!results.isArray() || results.isEmpty()) {
                    throw new ExternalProviderException("Geoapify konnte die Stadt nicht aufloesen.");
                }

                JsonNode location = results.get(0);
                latitude = latitude == null ? location.path("lat").asDouble() : latitude;
                longitude = longitude == null ? location.path("lon").asDouble() : longitude;
                bbox = bbox(location.path("bbox"));
            }

            double originLatitude = latitude;
            double originLongitude = longitude;
            CitySpatialContext context = CitySpatialContext.from(
                locationText,
                originLatitude,
                originLongitude,
                bbox,
                demand
            );
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
                MultiAreaImportPlan plan = areaPlanner().plan(context, interest, demand);
                int beforeInterestFetch = candidates.size();
                for (ImportArea area : plan.areas()) {
                    fetchArea(
                        locationText,
                        apiKey,
                        originLatitude,
                        originLongitude,
                        candidates,
                        seen,
                        interest,
                        categories,
                        area
                    );
                }
                logAreaQuality(plan, candidates.subList(beforeInterestFetch, candidates.size()), context);
            }
            populateNearbyShopDensity(candidates);
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
        copyRawTag(raw, candidate, "natural");
        copyRawTag(raw, candidate, "waterway");
        copyRawTag(raw, candidate, "amenity");
        copyRawTag(raw, candidate, "landuse");
        copyRawTag(raw, candidate, "cemetery");
        copyRawTag(raw, candidate, "funeral");
        copyRawTag(raw, candidate, "grave");
        copyRawTag(raw, candidate, "grave_yard");
        copyRawTag(raw, candidate, "leisure");
        copyRawTag(raw, candidate, "access");
        copyRawTag(raw, candidate, "operator");
        copyRawTag(raw, candidate, "ownership");
        copyRawTag(raw, candidate, "highway");
        copyRawTag(raw, candidate, "railway");
        copyRawTag(raw, candidate, "public_transport");
        copyRawTag(raw, candidate, "aeroway");
        copyRawTag(raw, candidate, "bridge");
        copyRawTag(raw, candidate, "level");
        copyRawTag(raw, candidate, "addr:floor");
        copyRawTag(raw, candidate, "platform");
        copyRawTag(raw, candidate, "subway");
        copyRawTag(raw, candidate, "rail");
        copyRawTag(raw, candidate, "terminal");
        copyRawTag(raw, candidate, "emergency");
        copyRawTag(raw, candidate, "parking");
        copyRawTag(raw, candidate, "tourism");
        copyRawTag(raw, candidate, "man_made");
        copyRawTag(raw, candidate, "shop");
        copyRawTag(raw, candidate, "building");
        copyRawTag(raw, candidate, "indoor");
        copyRawTag(raw, candidate, "office");
        copyRawTag(raw, candidate, "cuisine");
        copyRawTag(raw, candidate, "heritage");
        copyRawTag(raw, candidate, "garden:type");
        copyRawTag(raw, candidate, "garden");
        copyRawTag(raw, candidate, "botanical");
        copyRawTag(raw, candidate, "route");
        candidate.geometryAreaM2 = firstNonNull(number(raw, "area"), number(raw, "way_area"));
        String wikipedia = firstNonBlank(text(raw, "wikipedia"), text(raw, "wikipedia:de"));
        if (wikipedia != null) {
            candidate.externalRefs.put(ActivitySource.WIKIPEDIA, wikipedia.replaceFirst("^[a-z]{2}:", ""));
        }
        String osmId = text(raw, "osm_id");
        if (osmId != null) {
            candidate.externalRefs.put(ActivitySource.OPEN_STREET_MAP, osmId);
        }
        return candidate;
    }

    private static String conditionsFor(InterestType interest) {
        return interest == InterestType.NATURE ? "named,access" : null;
    }

    private void fetchArea(
        String locationText,
        String apiKey,
        double originLatitude,
        double originLongitude,
        List<ExternalActivityCandidate> candidates,
        Set<String> seen,
        InterestType interest,
        List<String> categories,
        ImportArea area
    ) {
        int rawFetched = 0;
        int maxRaw = Math.min(area.rawTarget(), settings().maxRawPerInterest());
        for (int page = 0; page < settings().maxPagesPerInterest() && rawFetched < maxRaw; page++) {
            int offset = page * settings().geoapifyPageSize();
            int pageLimit = Math.min(settings().geoapifyPageSize(), maxRaw - rawFetched);
            JsonNode features = client.places(
                String.join(",", categories),
                conditionsFor(interest),
                "circle:" + area.centerLon() + "," + area.centerLat() + "," + area.radiusMeters(),
                "proximity:" + area.centerLon() + "," + area.centerLat(),
                pageLimit,
                offset,
                "de",
                apiKey
            ).path("features");
            if (!features.isArray() || features.isEmpty()) {
                break;
            }
            rawFetched += features.size();
            int accepted = 0;
            for (JsonNode feature : features) {
                ExternalActivityCandidate candidate = candidate(feature.path("properties"), locationText);
                if (candidate == null || !filtering.isRelevant(candidate, interest) || filtering.isDuplicate(candidate, seen)) {
                    continue;
                }
                candidate.distanceToCenterKm = distanceInKilometers(
                    originLatitude,
                    originLongitude,
                    candidate.latitude,
                    candidate.longitude
                );
                candidate.primaryInterest = interest;
                candidate.matchedInterests.add(interest);
                candidates.add(candidate);
                accepted++;
            }
            LOG.debugf(
                "Geoapify import page city=%s interest=%s area=%s center=%.5f,%.5f radiusM=%d offset=%d limit=%d rawFetched=%d accepted=%d",
                locationText,
                interest,
                area.label(),
                area.centerLat(),
                area.centerLon(),
                area.radiusMeters(),
                offset,
                pageLimit,
                features.size(),
                accepted
            );
            if (features.size() < pageLimit) {
                break;
            }
        }
    }

    private static void copyRawTag(JsonNode raw, ExternalActivityCandidate candidate, String tag) {
        String value = text(raw, tag);
        if (value != null) {
            candidate.rawTags.put(tag, value);
        }
    }

    static void populateNearbyShopDensity(List<ExternalActivityCandidate> candidates) {
        for (ExternalActivityCandidate candidate : candidates) {
            if (candidate.latitude == null || candidate.longitude == null) {
                continue;
            }
            int density = 0;
            for (ExternalActivityCandidate neighbor : candidates) {
                if (candidate == neighbor || neighbor.latitude == null || neighbor.longitude == null) {
                    continue;
                }
                if (isRetailLike(neighbor)
                    && distanceInKilometers(
                        candidate.latitude,
                        candidate.longitude,
                        neighbor.latitude,
                        neighbor.longitude
                    ) <= 0.25) {
                    density++;
                }
            }
            candidate.nearbyShopDensity = density;
        }
    }

    private static boolean isRetailLike(ExternalActivityCandidate candidate) {
        return candidate.rawCategories.stream().anyMatch(category -> {
            String normalized = category.toLowerCase(Locale.ROOT);
            return normalized.equals("commercial")
                || normalized.startsWith("commercial.")
                || normalized.equals("shop")
                || normalized.startsWith("shop.");
        }) || candidate.rawTags.containsKey("shop")
            || "marketplace".equalsIgnoreCase(candidate.rawTags.get("amenity"));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Double number(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asDouble() : null;
    }

    private static CityBoundingBox bbox(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() >= 4) {
            return new CityBoundingBox(node.get(1).asDouble(), node.get(0).asDouble(), node.get(3).asDouble(), node.get(2).asDouble());
        }
        Double lon1 = number(node, "lon1");
        Double lat1 = number(node, "lat1");
        Double lon2 = number(node, "lon2");
        Double lat2 = number(node, "lat2");
        if (lon1 == null || lat1 == null || lon2 == null || lat2 == null) {
            return null;
        }
        return new CityBoundingBox(Math.min(lat1, lat2), Math.min(lon1, lon2), Math.max(lat1, lat2), Math.max(lon1, lon2));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }

    private static double distanceInKilometers(
        double firstLatitude,
        double firstLongitude,
        double secondLatitude,
        double secondLongitude
    ) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String safeFailureReason(RuntimeException exception) {
        Throwable cause = exception.getCause();
        String reason = cause == null ? exception.getMessage() : cause.getMessage();
        if (reason == null || reason.isBlank()) {
            return "unbekannt";
        }
        return reason.replaceAll("apiKey=[^&\\s]+", "apiKey=***");
    }

    private ActivityImportSettings settings() {
        return settings == null ? new ActivityImportSettings() : settings;
    }

    private void logAreaQuality(
        MultiAreaImportPlan plan,
        List<ExternalActivityCandidate> interestCandidates,
        CitySpatialContext context
    ) {
        if (plan.areas().size() <= 1 || interestCandidates.isEmpty()) {
            return;
        }
        AreaQualityService service = areaQuality == null ? new AreaQualityService() : areaQuality;
        boolean foodRelevant = context.selectedInterests().contains(InterestType.FOOD);
        for (ImportArea area : plan.areas()) {
            AreaQualityScore score = service.score(area, interestCandidates, context.selectedInterests(), foodRelevant);
            LOG.debugf(
                "Import area quality city=%s interest=%s area=%s score=%.2f daySuitable=%s candidates=%d",
                plan.city(),
                plan.interest(),
                area.label(),
                score.score(),
                score.daySuitable(),
                interestCandidates.size()
            );
        }
    }

    private MultiAreaImportPlanner areaPlanner() {
        if (areaPlanner == null) {
            areaPlanner = new MultiAreaImportPlanner();
            areaPlanner.settings = settings();
        }
        return areaPlanner;
    }

}
