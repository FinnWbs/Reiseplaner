package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class WikidataCatalogProvider {
    private static final Map<String, CategoryRule> CATEGORY_RULES = Map.ofEntries(
        Map.entry("Q33506", new CategoryRule(InterestType.CULTURE, "museum", 0.95)),
        Map.entry("Q207694", new CategoryRule(InterestType.CULTURE, "art_museum", 0.95)),
        Map.entry("Q16970", new CategoryRule(InterestType.CULTURE, "church", 0.88)),
        Map.entry("Q2977", new CategoryRule(InterestType.CULTURE, "cathedral", 0.95)),
        Map.entry("Q16560", new CategoryRule(InterestType.SIGHTSEEING, "palace", 0.95)),
        Map.entry("Q23413", new CategoryRule(InterestType.SIGHTSEEING, "castle", 0.95)),
        Map.entry("Q174782", new CategoryRule(InterestType.SIGHTSEEING, "square", 0.86)),
        Map.entry("Q12518", new CategoryRule(InterestType.SIGHTSEEING, "tower", 0.88)),
        Map.entry("Q1440300", new CategoryRule(InterestType.SIGHTSEEING, "observation_tower", 0.90)),
        Map.entry("Q4989906", new CategoryRule(InterestType.SIGHTSEEING, "monument", 0.78)),
        Map.entry("Q5003624", new CategoryRule(InterestType.SIGHTSEEING, "memorial", 0.72)),
        Map.entry("Q570116", new CategoryRule(InterestType.SIGHTSEEING, "tourist_attraction", 0.80)),
        Map.entry("Q2319498", new CategoryRule(InterestType.SIGHTSEEING, "landmark", 0.95)),
        Map.entry("Q210272", new CategoryRule(InterestType.CULTURE, "cultural_heritage", 0.88)),
        Map.entry("Q43501", new CategoryRule(InterestType.NATURE, "zoo", 0.82)),
        Map.entry("Q167346", new CategoryRule(InterestType.NATURE, "botanical_garden", 0.90)),
        Map.entry("Q22698", new CategoryRule(InterestType.NATURE, "park", 0.74)),
        Map.entry("Q1007870", new CategoryRule(InterestType.CULTURE, "art_gallery", 0.84))
    );

    @Inject
    @RestClient
    WikidataQueryClient client;

    @Inject
    CityBoundaryResolver boundaries;

    @Inject
    AttractionCatalogSettings settings;

    public List<WikimediaCatalogCandidate> candidatesFor(TripEntity trip) {
        return catalogFor(trip).candidates();
    }

    WikidataCatalogResult catalogFor(TripEntity trip) {
        CityBoundary boundary = boundaryFor(trip);
        if (boundary == null) {
            return WikidataCatalogResult.empty();
        }

        JsonNode bindings = client.query(
            candidateQuery(
                boundary.centerLat(),
                boundary.centerLon(),
                boundary.queryRadiusKm(settings.maxSearchRadiusKm()),
                settings.candidateLimit(),
                boundary.wikidataId()
            ),
            "json",
            settings.userAgent()
        ).path("results").path("bindings");

        Map<String, WikimediaCatalogCandidate> byWikidataId = new LinkedHashMap<>();
        if (!bindings.isArray()) {
            return new WikidataCatalogResult(boundary, List.of());
        }
        for (JsonNode binding : bindings) {
            String wikidataId = wikidataId(binding.path("item").path("value").asText(null));
            if (wikidataId == null) {
                continue;
            }
            WikimediaCatalogCandidate candidate = baseCandidate(trip, binding, wikidataId, boundary);
            applyBestCategory(candidate, value(binding, "typeIds"));
            if (candidate.primaryInterest == null || !isInsideCatalogBoundary(candidate, boundary)) {
                continue;
            }
            byWikidataId.putIfAbsent(wikidataId, candidate);
        }
        List<WikimediaCatalogCandidate> candidates = byWikidataId.values().stream()
            .filter(candidate -> candidate.name != null && !candidate.name.isBlank())
            .filter(candidate -> candidate.wikipediaTitle != null && !candidate.wikipediaTitle.isBlank())
            .filter(candidate -> candidate.latitude != null && candidate.longitude != null)
            .toList();
        return new WikidataCatalogResult(boundary, candidates);
    }

    private CityBoundary boundaryFor(TripEntity trip) {
        return boundaries == null ? null : boundaries.resolve(trip);
    }

    private static WikimediaCatalogCandidate baseCandidate(
        TripEntity trip,
        JsonNode binding,
        String wikidataId,
        CityBoundary boundary
    ) {
        WikimediaCatalogCandidate candidate = new WikimediaCatalogCandidate();
        candidate.city = trip.city;
        candidate.wikidataId = wikidataId;
        candidate.catalogId = CatalogCityKey.from(trip.city) + "-" + wikidataId.toLowerCase(Locale.ROOT);
        candidate.name = value(binding, "itemLabel");
        candidate.description = value(binding, "itemDescription");
        candidate.sitelinkCount = intValue(binding, "sitelinks");
        candidate.wikipediaProject = value(binding, "wikiProject");
        candidate.wikipediaTitle = decodeWikiTitle(value(binding, "wikipediaTitle"));
        candidate.hasImage = binding.has("image");
        candidate.hasWebsite = binding.has("website");
        parseCoordinate(candidate, value(binding, "coord"));
        candidate.hasCoordinates = candidate.latitude != null && candidate.longitude != null;
        candidate.administrativelyInCity = intValue(binding, "inCity") > 0;
        if (candidate.hasCoordinates) {
            candidate.distanceFromCityCenterKm = boundary.distanceKm(candidate.latitude, candidate.longitude);
        }
        return candidate;
    }

    private static void applyBestCategory(WikimediaCatalogCandidate candidate, String typeIds) {
        if (typeIds == null || typeIds.isBlank()) {
            return;
        }
        Arrays.stream(typeIds.split(","))
            .map(String::trim)
            .map(CATEGORY_RULES::get)
            .filter(Objects::nonNull)
            .forEach(rule -> {
                if (candidate.primaryInterest == null || rule.categoryFitScore > candidate.categoryFitScore) {
                    candidate.primaryInterest = rule.interest;
                    candidate.category = rule.category;
                    candidate.categoryFitScore = rule.categoryFitScore;
                }
            });
    }

    static boolean isInsideCatalogBoundary(WikimediaCatalogCandidate candidate, CityBoundary boundary) {
        if (candidate.latitude == null || candidate.longitude == null || boundary.rejectsHard(candidate.latitude, candidate.longitude)) {
            return false;
        }
        if (candidate.administrativelyInCity && boundary.acceptsCityMember(candidate.latitude, candidate.longitude)) {
            return true;
        }
        candidate.acceptedAsNearbyEnclave = boundary.acceptsNearbyEnclave(
            candidate.latitude,
            candidate.longitude,
            candidate.sitelinkCount,
            candidate.categoryFitScore
        );
        return candidate.acceptedAsNearbyEnclave;
    }

    static String candidateQuery(double latitude, double longitude, double radiusKm, int limit, String cityWikidataId) {
        int preLimit = Math.max(120, limit * 4);
        String cityMembershipClause = cityWikidataId == null || cityWikidataId.isBlank()
            ? "  BIND(0 AS ?inCityValue)\n"
            : String.format(Locale.ROOT, """
              OPTIONAL { ?item wdt:P131* wd:%s . BIND(1 AS ?inCityMatch) }
              BIND(IF(BOUND(?inCityMatch), 1, 0) AS ?inCityValue)
            """, cityWikidataId);
        return String.format(Locale.ROOT, """
            SELECT ?item ?itemLabel ?itemDescription ?coord
                   (GROUP_CONCAT(DISTINCT ?typeId; separator=",") AS ?typeIds)
                   (MAX(?sitelinksPre) AS ?sitelinks)
                   (MAX(?inCityValue) AS ?inCity)
                   (SAMPLE(?wikipediaTitleRaw) AS ?wikipediaTitle)
                   (SAMPLE(?wikiProjectRaw) AS ?wikiProject)
                   (SAMPLE(?imageRaw) AS ?image)
                   (SAMPLE(?websiteRaw) AS ?website)
            WHERE {
              {
                SELECT ?item ?coord (MAX(?sitelinksRaw) AS ?sitelinksPre)
                WHERE {
                  SERVICE wikibase:around {
                    ?item wdt:P625 ?coord .
                    bd:serviceParam wikibase:center "Point(%f %f)"^^geo:wktLiteral .
                    bd:serviceParam wikibase:radius "%f" .
                  }
                  ?item wikibase:sitelinks ?sitelinksRaw .
                  FILTER(?sitelinksRaw >= 3)
                  {
                    ?articleAny schema:about ?item ;
                      schema:isPartOf <https://de.wikipedia.org/> .
                  }
                  UNION
                  {
                    ?articleAny schema:about ?item ;
                      schema:isPartOf <https://en.wikipedia.org/> .
                  }
                }
                GROUP BY ?item ?coord
                ORDER BY DESC(?sitelinksPre)
                LIMIT %d
              }
              ?item wdt:P31/wdt:P279* ?type .
              VALUES ?type {
                wd:Q33506 wd:Q207694 wd:Q16970 wd:Q2977 wd:Q16560 wd:Q23413 wd:Q174782 wd:Q12518
                wd:Q1440300 wd:Q4989906 wd:Q5003624 wd:Q570116 wd:Q2319498 wd:Q210272 wd:Q43501
                wd:Q167346 wd:Q22698 wd:Q1007870
              }
              BIND(STRAFTER(STR(?type), "entity/") AS ?typeId)
            %s
              OPTIONAL { ?articleDe schema:about ?item ; schema:isPartOf <https://de.wikipedia.org/> . }
              OPTIONAL { ?articleEn schema:about ?item ; schema:isPartOf <https://en.wikipedia.org/> . }
              BIND(COALESCE(?articleDe, ?articleEn) AS ?article)
              FILTER(BOUND(?article))
              BIND(IF(BOUND(?articleDe), "de.wikipedia.org", "en.wikipedia.org") AS ?wikiProjectRaw)
              BIND(STRAFTER(STR(?article), "/wiki/") AS ?wikipediaTitleRaw)
              OPTIONAL { ?item wdt:P18 ?imageRaw . }
              OPTIONAL { ?item wdt:P856 ?websiteRaw . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "de,en" . }
            }
            GROUP BY ?item ?itemLabel ?itemDescription ?coord
            ORDER BY DESC(?sitelinks)
            LIMIT %d
            """, longitude, latitude, radiusKm, preLimit, cityMembershipClause, limit);
    }

    private static String value(JsonNode binding, String field) {
        String value = binding.path(field).path("value").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static int intValue(JsonNode binding, String field) {
        return binding.path(field).path("value").asInt(0);
    }

    private static String wikidataId(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        int index = uri.lastIndexOf('/');
        return index < 0 ? uri : uri.substring(index + 1);
    }

    private static String decodeWikiTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return URLDecoder.decode(title, StandardCharsets.UTF_8);
    }

    private static void parseCoordinate(WikimediaCatalogCandidate candidate, String wkt) {
        if (wkt == null || !wkt.startsWith("Point(") || !wkt.endsWith(")")) {
            return;
        }
        String[] parts = wkt.substring(6, wkt.length() - 1).split(" ");
        if (parts.length != 2) {
            return;
        }
        try {
            candidate.longitude = Double.parseDouble(parts[0]);
            candidate.latitude = Double.parseDouble(parts[1]);
        } catch (NumberFormatException ignored) {
            candidate.latitude = null;
            candidate.longitude = null;
        }
    }

    private record CategoryRule(InterestType interest, String category, double categoryFitScore) {}
}
