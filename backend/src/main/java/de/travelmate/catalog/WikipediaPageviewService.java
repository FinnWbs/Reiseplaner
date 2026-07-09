package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class WikipediaPageviewService {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM'01'00");

    @Inject
    @RestClient
    WikimediaPageviewsClient client;

    @Inject
    AttractionCatalogSettings settings;

    public void enrich(List<WikimediaCatalogCandidate> candidates) {
        List<WikimediaCatalogCandidate> candidatesToEnrich = candidatesForPageviews(candidates);
        if (candidatesToEnrich.isEmpty()) {
            return;
        }

        YearMonth endMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        YearMonth startMonth = endMonth.minusMonths(Math.max(1, settings.pageviewMonths()) - 1L);
        String start = startMonth.format(MONTH_FORMAT);
        String end = endMonth.format(MONTH_FORMAT);
        int parallelism = Math.max(1, Math.min(settings.pageviewParallelism(), candidatesToEnrich.size()));

        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            CompletableFuture<?>[] tasks = candidatesToEnrich.stream()
                .map(candidate -> CompletableFuture.runAsync(
                    () -> enrichCandidate(candidate, start, end),
                    executor
                ))
                .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(tasks).join();
        }
    }

    List<WikimediaCatalogCandidate> candidatesForPageviews(List<WikimediaCatalogCandidate> candidates) {
        int limit = Math.max(0, settings.pageviewCandidateLimit());
        if (limit == 0 || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
            .filter(candidate -> candidate.wikipediaProject != null && candidate.wikipediaTitle != null)
            .filter(candidate -> !candidate.wikipediaProject.isBlank() && !candidate.wikipediaTitle.isBlank())
            .sorted(Comparator
                .comparingInt((WikimediaCatalogCandidate candidate) -> candidate.sitelinkCount).reversed()
                .thenComparing(Comparator.comparingDouble((WikimediaCatalogCandidate candidate) -> candidate.categoryFitScore).reversed())
                .thenComparing(candidate -> candidate.name == null ? "" : candidate.name))
            .limit(limit)
            .toList();
    }

    private void enrichCandidate(WikimediaCatalogCandidate candidate, String start, String end) {
        try {
            JsonNode response = client.monthlyPageviews(
                candidate.wikipediaProject,
                candidate.wikipediaTitle,
                start,
                end,
                settings.userAgent()
            );
            long views = 0;
            for (JsonNode item : response.path("items")) {
                views += item.path("views").asLong(0);
            }
            candidate.pageviews = views;
        } catch (RuntimeException ignored) {
            candidate.pageviews = 0;
        }
    }
}
