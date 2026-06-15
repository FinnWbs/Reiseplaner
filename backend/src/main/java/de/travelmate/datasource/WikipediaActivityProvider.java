package de.travelmate.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import de.travelmate.activity.ActivitySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class WikipediaActivityProvider {
    @Inject
    @RestClient
    WikipediaClient client;

    public List<String> enrich(List<ExternalActivityCandidate> candidates) {
        List<String> warnings = new ArrayList<>();
        for (ExternalActivityCandidate candidate : candidates) {
            String title = candidate.externalRefs.get(ActivitySource.WIKIPEDIA);
            if (title == null) {
                continue;
            }
            try {
                JsonNode pages = client.extract("query", "extracts", true, true, true, title, "json")
                    .path("query")
                    .path("pages");
                Iterator<Map.Entry<String, JsonNode>> fields = pages.fields();
                if (fields.hasNext()) {
                    String extract = fields.next().getValue().path("extract").asText(null);
                    if (extract != null && !extract.isBlank()) {
                        candidate.description = extract;
                    }
                }
            } catch (RuntimeException exception) {
                warnings.add("Wikipedia-Beschreibung fuer " + candidate.name + " fehlgeschlagen.");
            }
        }
        return warnings;
    }
}
