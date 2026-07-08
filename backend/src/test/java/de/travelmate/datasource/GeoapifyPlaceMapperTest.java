package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.travelmate.activity.ActivitySource;
import org.junit.jupiter.api.Test;

class GeoapifyPlaceMapperTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapsPlacePropertiesAndRawTags() {
        var properties = MAPPER.createObjectNode();
        properties.put("name", "Botanischer Garten");
        properties.put("place_id", "geo-1");
        properties.put("formatted", "Berlin, Deutschland");
        properties.put("lat", 52.45);
        properties.put("lon", 13.30);
        properties.putArray("categories").add("leisure.park.garden");
        var raw = properties.putObject("datasource").putObject("raw");
        raw.put("wikidata", "Q123");
        raw.put("wikipedia", "de:Botanischer Garten Berlin");
        raw.put("leisure", "garden");
        raw.put("garden:type", "botanical");
        raw.put("osm_id", "987");

        ExternalActivityCandidate candidate = new GeoapifyPlaceMapper().candidate(properties, "Berlin");

        assertEquals("Botanischer Garten", candidate.name);
        assertEquals("geo-1", candidate.externalRefs.get(ActivitySource.GEOAPIFY));
        assertEquals("Q123", candidate.externalRefs.get(ActivitySource.WIKIDATA));
        assertEquals("Botanischer Garten Berlin", candidate.externalRefs.get(ActivitySource.WIKIPEDIA));
        assertEquals("garden", candidate.rawTags.get("leisure"));
        assertEquals("botanical", candidate.rawTags.get("garden:type"));
        assertTrue(candidate.hasWikidata);
    }

    @Test
    void mapsGeoapifyBoundingBoxFormats() {
        var arrayBox = MAPPER.createArrayNode()
            .add(13.0)
            .add(52.0)
            .add(14.0)
            .add(53.0);

        CityBoundingBox bbox = new GeoapifyPlaceMapper().boundingBox(arrayBox);

        assertEquals(52.0, bbox.minLat());
        assertEquals(13.0, bbox.minLon());
        assertEquals(53.0, bbox.maxLat());
        assertEquals(14.0, bbox.maxLon());
    }
}
