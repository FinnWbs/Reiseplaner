package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivityImportSettings;
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
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GeoapifyActivityProvider implements ActivityCandidateSource {
    private static final Logger LOG = Logger.getLogger(GeoapifyActivityProvider.class);
    private static final String MARKETPLACE_CATEGORY = "commercial.marketplace";

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

    @Inject
    GeoapifyPlaceMapper placeMapper;

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
                bbox = mapper().boundingBox(location.path("bbox"));
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
                if (interest == InterestType.SHOPPING) {
                    fetchMarketplaces(
                        locationText,
                        selectedPlaceId,
                        apiKey,
                        originLatitude,
                        originLongitude,
                        candidates,
                        seen,
                        plan,
                        demand
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
                ExternalActivityCandidate candidate = mapper().candidate(feature.path("properties"), locationText);
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

    private void fetchMarketplaces(
        String locationText,
        String selectedPlaceId,
        String apiKey,
        double originLatitude,
        double originLongitude,
        List<ExternalActivityCandidate> candidates,
        Set<String> seen,
        MultiAreaImportPlan plan,
        ImportDemand demand
    ) {
        int target = marketplaceTarget(demand);
        if (target <= 0) {
            return;
        }
        List<ImportArea> marketplaceAreas = rebudgetAreas(plan.areas(), target);
        int totalFetched = 0;
        int totalAccepted = 0;
        String filterMode = selectedPlaceId == null || selectedPlaceId.isBlank() ? "circle" : "place";
        for (ImportArea area : marketplaceAreas) {
            FetchStats stats = fetchMarketplaceArea(
                locationText,
                selectedPlaceId,
                apiKey,
                originLatitude,
                originLongitude,
                candidates,
                seen,
                area
            );
            totalFetched += stats.fetched();
            totalAccepted += stats.accepted();
        }
        LOG.debugf(
            "Geoapify marketplace import city=%s target=%d fetched=%d accepted=%d filter=%s",
            locationText,
            target,
            totalFetched,
            totalAccepted,
            filterMode
        );
    }

    private FetchStats fetchMarketplaceArea(
        String locationText,
        String selectedPlaceId,
        String apiKey,
        double originLatitude,
        double originLongitude,
        List<ExternalActivityCandidate> candidates,
        Set<String> seen,
        ImportArea area
    ) {
        int rawFetched = 0;
        int totalAccepted = 0;
        int maxRaw = Math.min(area.rawTarget(), settings().maxRawPerInterest());
        String filter = marketplaceFilter(selectedPlaceId, area);
        for (int page = 0; page < settings().maxPagesPerInterest() && rawFetched < maxRaw; page++) {
            int offset = page * settings().geoapifyPageSize();
            int pageLimit = Math.min(settings().geoapifyPageSize(), maxRaw - rawFetched);
            JsonNode features = client.places(
                MARKETPLACE_CATEGORY,
                null,
                filter,
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
            for (JsonNode feature : features) {
                ExternalActivityCandidate candidate = mapper().candidate(feature.path("properties"), locationText);
                if (candidate == null
                    || !filtering.isRelevant(candidate, InterestType.SHOPPING)
                    || filtering.isDuplicate(candidate, seen)) {
                    continue;
                }
                candidate.distanceToCenterKm = distanceInKilometers(
                    originLatitude,
                    originLongitude,
                    candidate.latitude,
                    candidate.longitude
                );
                candidate.primaryInterest = InterestType.SHOPPING;
                candidate.matchedInterests.add(InterestType.SHOPPING);
                candidates.add(candidate);
                totalAccepted++;
            }
            if (features.size() < pageLimit) {
                break;
            }
        }
        return new FetchStats(rawFetched, totalAccepted);
    }

    private int marketplaceTarget(ImportDemand demand) {
        if (demand == null) {
            return settings().shoppingMarketplaceDefaultTarget();
        }
        return settings().shoppingMarketplaceTargetForTripDays(demand.tripDays());
    }

    private String marketplaceFilter(String selectedPlaceId, ImportArea area) {
        if (selectedPlaceId != null && !selectedPlaceId.isBlank()) {
            return "place:" + selectedPlaceId.trim();
        }
        return "circle:" + area.centerLon() + "," + area.centerLat() + "," + area.radiusMeters();
    }

    private static List<ImportArea> rebudgetAreas(List<ImportArea> sourceAreas, int target) {
        if (sourceAreas == null || sourceAreas.isEmpty() || target <= 0) {
            return List.of();
        }
        int sourceTotal = sourceAreas.stream().mapToInt(ImportArea::rawTarget).sum();
        if (sourceTotal <= 0) {
            sourceTotal = sourceAreas.size();
        }
        List<ImportArea> result = new ArrayList<>();
        int remaining = target;
        for (int index = 0; index < sourceAreas.size(); index++) {
            ImportArea area = sourceAreas.get(index);
            int budget;
            if (index == sourceAreas.size() - 1) {
                budget = remaining;
            } else {
                double share = Math.max(1, area.rawTarget()) / (double) sourceTotal;
                budget = Math.max(1, (int) Math.round(target * share));
                budget = Math.min(budget, Math.max(0, remaining - (sourceAreas.size() - index - 1)));
            }
            remaining -= budget;
            if (budget <= 0) {
                continue;
            }
            result.add(new ImportArea(
                area.id() + "-marketplace",
                area.label() + " Marketplace",
                area.centerLat(),
                area.centerLon(),
                area.radiusMeters(),
                budget / (double) target,
                budget,
                area.areaType(),
                area.distanceFromCityCenterKm(),
                area.reachable()
            ));
        }
        return result;
    }

    private record FetchStats(int fetched, int accepted) {}

    static void populateNearbyShopDensity(List<ExternalActivityCandidate> candidates) {
        NearbyShopDensityCalculator.populate(candidates);
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

    private GeoapifyPlaceMapper mapper() {
        return placeMapper == null ? new GeoapifyPlaceMapper() : placeMapper;
    }

}
