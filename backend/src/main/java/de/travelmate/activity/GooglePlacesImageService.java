package de.travelmate.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GooglePlacesImageService {
    private static final Logger LOG = Logger.getLogger(GooglePlacesImageService.class);
    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String FIELD_MASK = "places.id,places.displayName,places.formattedAddress,places.location,places.photos";
    private static final int MAX_IMAGES = 3;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivityExternalRefRepository externalRefs;

    @Inject
    ActivityImageRepository images;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "travelmate.google-places.api-key")
    Optional<String> configuredApiKey;

    @ConfigProperty(name = "travelmate.google-places.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "travelmate.google-places.timeout-ms", defaultValue = "3000")
    long timeoutMs;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Transactional
    public List<ActivityImageDto> ensureImages(Long activityId) {
        ActivityEntity activity = activities.findById(activityId, LockModeType.PESSIMISTIC_WRITE);
        if (activity == null) {
            throw new NotFoundException("Aktivitaet nicht gefunden.");
        }
        List<ActivityImageDto> existing = currentImages(activity.id);
        LOG.debug(String.format(
            "Image enrichment start: activityId=%d, activity=%s, existingImages=%d",
            activity.id,
            activity.name,
            existing.size()
        ));
        if (!existing.isEmpty()) {
            LOG.debug(String.format("Image enrichment existing: activityId=%d, images=%d", activity.id, existing.size()));
            return existing;
        }
        if (!enabled || apiKey().isEmpty()) {
            LOG.debug(String.format("Image enrichment disabled: activityId=%d", activity.id));
            return List.of();
        }

        try {
            Optional<GooglePlaceMatch> match = findGooglePlace(activity);
            if (match.isEmpty()) {
                LOG.debug(String.format("Image enrichment no match: activityId=%d, activity=%s", activity.id, activity.name));
                return List.of();
            }
            GooglePlaceMatch place = match.get();
            LOG.debug(String.format(
                "Image enrichment match: activityId=%d, placeId=%s, name=%s, score=%.3f, photos=%d",
                activity.id,
                place.placeId(),
                place.name(),
                place.score(),
                place.photos().size()
            ));
            mergeGooglePlaceRef(activity, place.placeId());
            persistImages(activity, place);
            activities.flush();
            List<ActivityImageDto> saved = currentImages(activity.id);
            LOG.debug(String.format("Image enrichment saved: activityId=%d, images=%d", activity.id, saved.size()));
            return saved;
        } catch (RuntimeException exception) {
            LOG.warnf(
                "Google Places image enrichment skipped: activityId=%d, activity=%s, reason=%s",
                activity.id,
                activity.name,
                safeReason(exception)
            );
            List<ActivityImageDto> recovered = currentImages(activity.id);
            LOG.debug(String.format(
                "Image enrichment recovered after exception: activityId=%d, images=%d",
                activity.id,
                recovered.size()
            ));
            return recovered;
        }
    }

    private List<ActivityImageDto> currentImages(Long activityId) {
        return images.findForActivity(activityId).stream().map(ActivityImageDto::from).toList();
    }

    public String resolvePhotoUri(Long activityId, Long imageId, int maxWidthPx) {
        ActivityImageEntity image = images.findByIdOptional(imageId)
            .filter(found -> found.activity != null && found.activity.id.equals(activityId))
            .orElseThrow(() -> new NotFoundException("Bild nicht gefunden."));
        if (image.source != ActivitySource.GOOGLE_PLACES) {
            if (image.url != null && !image.url.isBlank()) {
                return image.url;
            }
            throw new NotFoundException("Bild nicht gefunden.");
        }
        if (!enabled || apiKey().isEmpty()) {
            throw new ServiceUnavailableException("Google Places ist nicht konfiguriert.");
        }
        return fetchPhotoUri(image.providerRef, Math.max(320, Math.min(maxWidthPx, 1600)));
    }

    private Optional<GooglePlaceMatch> findGooglePlace(ActivityEntity activity) {
        GooglePlaceMatch best = null;
        for (String query : searchQueries(activity)) {
            JsonNode response = postSearch(activity, query);
            JsonNode places = response.path("places");
            if (!places.isArray() || places.isEmpty()) {
                continue;
            }
            for (JsonNode place : places) {
                GooglePlaceMatch candidate = toMatch(activity, place);
                if (candidate == null || !isAcceptable(activity, candidate)) {
                    continue;
                }
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
            if (best != null && best.score() >= 0.82) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private JsonNode postSearch(ActivityEntity activity, String textQuery) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("textQuery", textQuery);
            body.put("languageCode", "de");
            body.put("maxResultCount", 8);
            if (activity.latitude != null && activity.longitude != null) {
                body.put("locationBias", Map.of(
                    "circle",
                    Map.of(
                        "center",
                        Map.of("latitude", activity.latitude, "longitude", activity.longitude),
                        "radius",
                        strictCategory(activity) ? 1500.0 : 3000.0
                    )
                ));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey().orElseThrow())
                .header("X-Goog-FieldMask", FIELD_MASK)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google Places Text Search HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Google Places Text Search konnte nicht gelesen werden.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Places Text Search wurde unterbrochen.", exception);
        }
    }

    private GooglePlaceMatch toMatch(ActivityEntity activity, JsonNode place) {
        String placeId = text(place, "id");
        String name = text(place.path("displayName"), "text");
        String address = text(place, "formattedAddress");
        Double latitude = number(place.path("location"), "latitude");
        Double longitude = number(place.path("location"), "longitude");
        JsonNode photos = place.path("photos");
        if (placeId == null || name == null || !photos.isArray() || photos.isEmpty()) {
            return null;
        }
        double nameScore = similarity(activity.name, name);
        double addressScore = similarity(activity.address, address);
        Double distanceMeters = distanceMeters(activity.latitude, activity.longitude, latitude, longitude);
        double distanceScore = distanceMeters == null ? 0.0 : Math.max(0.0, 1.0 - (distanceMeters / 600.0));
        double score = nameScore * 0.58 + addressScore * 0.25 + distanceScore * 0.17;
        return new GooglePlaceMatch(placeId, name, address, latitude, longitude, photos, nameScore, addressScore, distanceMeters, score);
    }

    private boolean isAcceptable(ActivityEntity activity, GooglePlaceMatch match) {
        boolean strict = strictCategory(activity);
        double minimumName = strict ? 0.62 : 0.58;
        double maximumDistance = strict ? 650.0 : 1200.0;
        if (match.nameScore() < minimumName) {
            return false;
        }
        if (hasDifferentStreetNumber(activity.address, match.address()) && strict) {
            return false;
        }
        boolean strongAddress = match.addressScore() >= (strict ? 0.42 : 0.34);
        boolean closeEnough = match.distanceMeters() != null && match.distanceMeters() <= maximumDistance;
        boolean veryClose = match.distanceMeters() != null && match.distanceMeters() <= 180.0;
        boolean veryStrongName = match.nameScore() >= 0.86 && match.distanceMeters() != null && match.distanceMeters() <= 1800.0;
        if (strict) {
            return closeEnough || veryClose || strongAddress && match.distanceMeters() != null && match.distanceMeters() <= 900.0 || veryStrongName;
        }
        return strongAddress || closeEnough || veryClose || veryStrongName;
    }

    private void persistImages(ActivityEntity activity, GooglePlaceMatch place) {
        int order = 0;
        Set<String> seen = new HashSet<>();
        for (JsonNode photo : place.photos()) {
            if (order >= MAX_IMAGES) {
                break;
            }
            String photoName = text(photo, "name");
            if (photoName == null || !seen.add(photoName)) {
                continue;
            }
            ActivityImageEntity image = new ActivityImageEntity();
            image.activity = activity;
            image.source = ActivitySource.GOOGLE_PLACES;
            image.providerRef = photoName;
            image.alt = "Foto von " + activity.name;
            image.credit = credit(photo);
            image.sortOrder = order++;
            activity.images.add(image);
            images.persist(image);
        }
    }

    private void mergeGooglePlaceRef(ActivityEntity activity, String placeId) {
        boolean alreadyPresent = activity.externalRefs.stream().anyMatch(ref ->
            ref.source == ActivitySource.GOOGLE_PLACES && ref.externalId.equals(placeId)
        );
        if (alreadyPresent) {
            return;
        }
        Optional<ActivityExternalRefEntity> global =
            externalRefs.findBySourceAndExternalId(ActivitySource.GOOGLE_PLACES, placeId);
        if (global.isPresent() && !global.get().activity.id.equals(activity.id)) {
            return;
        }
        ActivityExternalRefEntity ref = new ActivityExternalRefEntity();
        ref.activity = activity;
        ref.source = ActivitySource.GOOGLE_PLACES;
        ref.externalId = placeId;
        activity.externalRefs.add(ref);
        externalRefs.persist(ref);
    }

    private String fetchPhotoUri(String photoName, int maxWidthPx) {
        try {
            String url = "https://places.googleapis.com/v1/" + photoName
                + "/media?maxWidthPx=" + maxWidthPx + "&skipHttpRedirect=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("X-Goog-Api-Key", apiKey().orElseThrow())
                .header("X-Goog-FieldMask", "photoUri")
                .GET()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google Places Photo HTTP " + response.statusCode());
            }
            String photoUri = text(objectMapper.readTree(response.body()), "photoUri");
            if (photoUri == null || !photoUri.startsWith("https://")) {
                throw new IllegalStateException("Google Places Photo lieferte keine HTTPS-URL.");
            }
            return photoUri;
        } catch (IOException exception) {
            throw new IllegalStateException("Google Places Photo konnte nicht gelesen werden.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Places Photo wurde unterbrochen.", exception);
        }
    }

    private String searchQuery(ActivityEntity activity) {
        return List.of(activity.name, activity.address, activity.city).stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse(activity.name);
    }

    private List<String> searchQueries(ActivityEntity activity) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(searchQuery(activity));
        addQuery(queries, activity.name, activity.city);
        addQuery(queries, activity.name, categoryHint(activity), activity.city);
        addQuery(queries, activity.name);
        return queries.stream().filter(query -> query != null && !query.isBlank()).toList();
    }

    private static void addQuery(Set<String> queries, String... parts) {
        String query = Arrays.stream(parts)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .reduce((first, second) -> first + ", " + second)
            .orElse(null);
        if (query != null && !query.isBlank()) {
            queries.add(query);
        }
    }

    private String categoryHint(ActivityEntity activity) {
        if (activity.primaryInterest == null) {
            return null;
        }
        return switch (activity.primaryInterest) {
            case FOOD -> "Restaurant";
            case SHOPPING -> "Markt oder Geschäft";
            case NATURE -> "Park";
            case HISTORY -> "Sehenswürdigkeit";
            case NIGHTLIFE -> "Bar";
            case ADVENTURE -> "Aktivität";
            default -> "Museum";
        };
    }

    private boolean strictCategory(ActivityEntity activity) {
        return activity.primaryInterest == de.travelmate.interest.InterestType.FOOD
            || activity.primaryInterest == de.travelmate.interest.InterestType.SHOPPING
            || activity.primaryInterest == de.travelmate.interest.InterestType.NIGHTLIFE;
    }

    private static double similarity(String first, String second) {
        Set<String> a = tokens(first);
        Set<String> b = tokens(second);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        double score = (double) intersection.size() / (double) union.size();
        String na = normalize(first);
        String nb = normalize(second);
        if (!na.isBlank() && !nb.isBlank() && (na.contains(nb) || nb.contains(na))) {
            score = Math.max(score, 0.86);
        }
        return score;
    }

    private static Set<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2 && !Set.of("ab", "ag", "gmbh", "ltd", "the", "and").contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static boolean hasDifferentStreetNumber(String first, String second) {
        String a = firstNumber(first);
        String b = firstNumber(second);
        return a != null && b != null && !a.equals(b) && similarity(first, second) < 0.62;
    }

    private static String firstNumber(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\d+[a-zA-Z]?\\b").matcher(value);
        return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : null;
    }

    private static Double distanceMeters(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double earth = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Optional<String> apiKey() {
        return configuredApiKey.map(String::trim).filter(value -> !value.isBlank());
    }

    private static String credit(JsonNode photo) {
        JsonNode attributions = photo.path("authorAttributions");
        if (!attributions.isArray() || attributions.isEmpty()) {
            return "Google Places";
        }
        String displayName = text(attributions.get(0), "displayName");
        return displayName == null ? "Google Places" : displayName;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private static String safeReason(Throwable exception) {
        return exception.getClass().getSimpleName() + ": " + Optional.ofNullable(exception.getMessage()).orElse("");
    }

    private record GooglePlaceMatch(
        String placeId,
        String name,
        String address,
        Double latitude,
        Double longitude,
        JsonNode photos,
        double nameScore,
        double addressScore,
        Double distanceMeters,
        double score
    ) {}
}
