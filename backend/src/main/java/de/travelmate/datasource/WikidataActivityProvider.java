package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivitySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class WikidataActivityProvider {
    @Inject
    @RestClient
    WikidataClient client;

    public List<String> enrich(List<ExternalActivityCandidate> candidates) {
        List<String> warnings = new ArrayList<>();
        for (ExternalActivityCandidate candidate : candidates) {
            String id = candidate.externalRefs.get(ActivitySource.WIKIDATA);
            if (id == null) {
                continue;
            }
            try {
                JsonNode entity = client.entity(id).path("entities").path(id);
                if (candidate.description == null) {
                    candidate.description = localizedValue(entity.path("descriptions"));
                }
                String label = localizedValue(entity.path("labels"));
                if ((candidate.name == null || candidate.name.isBlank()) && label != null) {
                    candidate.name = label;
                }
                String title = sitelinkTitle(entity.path("sitelinks"));
                if (title != null) {
                    candidate.externalRefs.put(ActivitySource.WIKIPEDIA, title);
                }
            } catch (RuntimeException exception) {
                warnings.add("Wikidata-Anreicherung fuer " + candidate.name + " fehlgeschlagen.");
            }
        }
        return warnings;
    }

    private static String localizedValue(JsonNode values) {
        JsonNode localized = values.path("de");
        if (localized.isMissingNode()) {
            localized = values.path("en");
        }
        String value = localized.path("value").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String sitelinkTitle(JsonNode sitelinks) {
        JsonNode link = sitelinks.path("dewiki");
        if (link.isMissingNode()) {
            link = sitelinks.path("enwiki");
        }
        String title = link.path("title").asText(null);
        return title == null || title.isBlank() ? null : title;
    }
}
