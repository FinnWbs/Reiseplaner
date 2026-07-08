package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.travelmate.datasource.CityBoundingBox;
import de.travelmate.trip.TripEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class WikidataCatalogProviderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void candidateQueryDeduplicatesItemsBeforeLimit() {
        String query = WikidataCatalogProvider.candidateQuery(41.9028, 12.4964, 18.0, 60, "Q220");

        assertTrue(query.contains("GROUP_CONCAT(DISTINCT ?typeId"));
        assertTrue(query.contains("GROUP BY ?item ?itemLabel ?itemDescription ?coord"));
        assertTrue(query.contains("?item wdt:P131* wd:Q220"));
        assertTrue(query.contains("LIMIT 60"));
    }

    @Test
    void filtersOutFarWolfsburgHighlightsForBraunschweig() {
        CityBoundary boundary = CityBoundary.fromBoundingBox(
            "Braunschweig",
            "Q2773",
            52.2689,
            10.5268,
            new CityBoundingBox(52.18, 10.43, 52.34, 10.62),
            1.5,
            3.0
        );
        FakeQueryClient query = new FakeQueryClient(
            binding("Q3799", "Braunschweiger Dom", "Q2977,Q210272", 52.2642, 10.5238, 64, 1),
            binding("Q697522", "Autostadt", "Q33506,Q570116", 52.4319, 10.7915, 86, 0),
            binding("Q1480903", "Phaeno", "Q33506,Q570116", 52.4289, 10.7895, 82, 0),
            binding("Q564264", "Schloss Wolfenbüttel", "Q16560,Q210272", 52.1621, 10.5368, 74, 0)
        );

        List<WikimediaCatalogCandidate> candidates = provider(boundary, query).candidatesFor(trip("Braunschweig"));

        assertEquals(1, candidates.size());
        assertEquals("Braunschweiger Dom", candidates.get(0).name);
    }

    @Test
    void allowsStrongNearbyEnclaveInsideDynamicRomeBoundary() {
        CityBoundary boundary = CityBoundary.fromBoundingBox(
            "Rom",
            "Q220",
            41.9028,
            12.4964,
            new CityBoundingBox(41.75, 12.35, 42.05, 12.65),
            1.5,
            3.0
        );
        FakeQueryClient query = new FakeQueryClient(
            binding("Q12512", "Petersdom", "Q2977,Q2319498", 41.9022, 12.4539, 320, 0)
        );

        List<WikimediaCatalogCandidate> candidates = provider(boundary, query).candidatesFor(trip("Rom"));

        assertEquals(1, candidates.size());
        assertEquals("Petersdom", candidates.get(0).name);
        assertTrue(candidates.get(0).acceptedAsNearbyEnclave);
    }

    @Test
    void groupedRomeRowsReturnMoreThanTwoUniqueHighlights() {
        CityBoundary boundary = CityBoundary.fromBoundingBox(
            "Rom",
            "Q220",
            41.9028,
            12.4964,
            new CityBoundingBox(41.75, 12.35, 42.05, 12.65),
            1.5,
            3.0
        );
        FakeQueryClient query = new FakeQueryClient(
            binding("Q10285", "Kolosseum", "Q570116,Q2319498", 41.8902, 12.4922, 360, 1),
            binding("Q99309", "Pantheon", "Q16970,Q570116", 41.8986, 12.4769, 310, 1),
            binding("Q193466", "Forum Romanum", "Q570116,Q210272", 41.8925, 12.4853, 290, 1),
            binding("Q185382", "Trevi-Brunnen", "Q570116,Q2319498", 41.9009, 12.4833, 280, 1)
        );

        List<WikimediaCatalogCandidate> candidates = provider(boundary, query).candidatesFor(trip("Rom"));

        assertEquals(4, candidates.size());
        assertFalse(candidates.stream().anyMatch(candidate -> candidate.primaryInterest == null));
    }

    private static WikidataCatalogProvider provider(CityBoundary boundary, FakeQueryClient query) {
        AttractionCatalogSettings settings = new AttractionCatalogSettings();
        settings.candidateLimit = 60;
        settings.maxSearchRadiusKm = 80;
        settings.userAgent = "TravelMate-Test/1.0";

        WikidataCatalogProvider provider = new WikidataCatalogProvider();
        provider.client = query;
        provider.boundaries = new FixedBoundaryResolver(boundary);
        provider.settings = settings;
        return provider;
    }

    private static TripEntity trip(String city) {
        TripEntity trip = new TripEntity();
        trip.city = city;
        return trip;
    }

    private static JsonNode binding(
        String wikidataId,
        String name,
        String typeIds,
        double latitude,
        double longitude,
        int sitelinks,
        int inCity
    ) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set("item", value("http://www.wikidata.org/entity/" + wikidataId));
        node.set("itemLabel", value(name));
        node.set("itemDescription", value("Highlight in der Stadt"));
        node.set("coord", value("Point(" + longitude + " " + latitude + ")"));
        node.set("typeIds", value(typeIds));
        node.set("sitelinks", value(Integer.toString(sitelinks)));
        node.set("inCity", value(Integer.toString(inCity)));
        node.set("wikipediaTitle", value(name.replace(' ', '_')));
        node.set("wikiProject", value("de.wikipedia.org"));
        node.set("image", value("https://example.invalid/image.jpg"));
        return node;
    }

    private static ObjectNode value(String value) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("value", value);
        return node;
    }

    private static class FixedBoundaryResolver extends CityBoundaryResolver {
        private final CityBoundary boundary;

        FixedBoundaryResolver(CityBoundary boundary) {
            this.boundary = boundary;
        }

        @Override
        public CityBoundary resolve(TripEntity trip) {
            return boundary;
        }
    }

    private static class FakeQueryClient implements WikidataQueryClient {
        private final JsonNode result;

        FakeQueryClient(JsonNode... bindings) {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode results = root.putObject("results");
            ArrayNode array = results.putArray("bindings");
            for (JsonNode binding : bindings) {
                array.add(binding);
            }
            this.result = root;
        }

        @Override
        public JsonNode query(String query, String format, String userAgent) {
            return result;
        }
    }
}
