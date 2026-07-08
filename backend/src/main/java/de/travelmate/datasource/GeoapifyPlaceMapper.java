package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivitySource;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GeoapifyPlaceMapper {
    public ExternalActivityCandidate candidate(JsonNode properties, String city) {
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
        copyRawTags(candidate, raw);
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

    public CityBoundingBox boundingBox(JsonNode node) {
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

    private static void copyRawTags(ExternalActivityCandidate candidate, JsonNode raw) {
        for (String tag : RAW_TAGS) {
            copyRawTag(raw, candidate, tag);
        }
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

    private static Double firstNonNull(Double first, Double second) {
        return first != null ? first : second;
    }

    private static final String[] RAW_TAGS = {
        "historic",
        "memorial",
        "memorial:type",
        "artwork_type",
        "natural",
        "waterway",
        "amenity",
        "landuse",
        "cemetery",
        "funeral",
        "grave",
        "grave_yard",
        "leisure",
        "access",
        "operator",
        "ownership",
        "highway",
        "railway",
        "public_transport",
        "aeroway",
        "bridge",
        "level",
        "addr:floor",
        "platform",
        "subway",
        "rail",
        "terminal",
        "emergency",
        "parking",
        "tourism",
        "man_made",
        "shop",
        "building",
        "indoor",
        "office",
        "cuisine",
        "heritage",
        "garden:type",
        "garden",
        "botanical",
        "route"
    };
}
