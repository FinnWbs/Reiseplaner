package de.travelmate.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        YearMonth endMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        YearMonth startMonth = endMonth.minusMonths(Math.max(1, settings.pageviewMonths()) - 1L);
        String start = startMonth.format(MONTH_FORMAT);
        String end = endMonth.format(MONTH_FORMAT);

        for (WikimediaCatalogCandidate candidate : candidates) {
            if (candidate.wikipediaProject == null || candidate.wikipediaTitle == null) {
                continue;
            }
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
}
