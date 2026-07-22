package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WikipediaPageviewServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void selectsOnlyTopCandidatesBySitelinksForPageviews() {
        WikipediaPageviewService service = new WikipediaPageviewService();
        service.settings = settings(3, 2);

        List<WikimediaCatalogCandidate> selected = service.candidatesForPageviews(List.of(
            candidate("small", 4, 0.95),
            candidate("largest", 100, 0.70),
            candidate("medium", 50, 0.80),
            candidate("no-wiki", 200, 0.99, false),
            candidate("second", 90, 0.70)
        ));

        assertEquals(List.of("largest", "second", "medium"), selected.stream().map(candidate -> candidate.name).toList());
    }

    @Test
    void loadsPageviewsInParallelWithConfiguredLimit() {
        WikipediaPageviewService service = new WikipediaPageviewService();
        service.settings = settings(8, 4);
        TrackingPageviewsClient client = new TrackingPageviewsClient();
        service.client = client;

        List<WikimediaCatalogCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            candidates.add(candidate("candidate-" + index, 100 - index, 0.80));
        }

        service.enrich(candidates);

        assertEquals(8, client.requestedTitles.size());
        assertTrue(client.maxConcurrent.get() > 1);
        assertTrue(client.maxConcurrent.get() <= 4);
        assertTrue(candidates.stream().allMatch(candidate -> candidate.pageviews == 42));
    }

    private static AttractionCatalogSettings settings(int pageviewCandidateLimit, int parallelism) {
        AttractionCatalogSettings settings = new AttractionCatalogSettings();
        settings.pageviewMonths = 1;
        settings.pageviewCandidateLimit = pageviewCandidateLimit;
        settings.pageviewParallelism = parallelism;
        settings.userAgent = "TravelMate-Test/1.0";
        return settings;
    }

    private static WikimediaCatalogCandidate candidate(String name, int sitelinks, double categoryFitScore) {
        return candidate(name, sitelinks, categoryFitScore, true);
    }

    private static WikimediaCatalogCandidate candidate(
        String name,
        int sitelinks,
        double categoryFitScore,
        boolean hasWikipedia
    ) {
        WikimediaCatalogCandidate candidate = new WikimediaCatalogCandidate();
        candidate.name = name;
        candidate.sitelinkCount = sitelinks;
        candidate.categoryFitScore = categoryFitScore;
        if (hasWikipedia) {
            candidate.wikipediaProject = "de.wikipedia.org";
            candidate.wikipediaTitle = name;
        }
        return candidate;
    }

    private static class TrackingPageviewsClient implements WikimediaPageviewsClient {
        private final AtomicInteger concurrent = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final List<String> requestedTitles = new CopyOnWriteArrayList<>();

        @Override
        public JsonNode monthlyPageviews(String project, String article, String start, String end, String userAgent) {
            int active = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(active, Math::max);
            requestedTitles.add(article);
            try {
                Thread.sleep(80);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
            ObjectNode root = MAPPER.createObjectNode();
            root.putArray("items").addObject().put("views", 42);
            return root;
        }
    }
}
