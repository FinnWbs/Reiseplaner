package de.travelmate.datasource;

import java.util.List;

public interface ActivityEnricher {
    List<String> enrich(List<ExternalActivityCandidate> candidates);
}
