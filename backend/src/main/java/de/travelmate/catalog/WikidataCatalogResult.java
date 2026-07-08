package de.travelmate.catalog;

import java.util.List;

record WikidataCatalogResult(
    CityBoundary boundary,
    List<WikimediaCatalogCandidate> candidates
) {
    static WikidataCatalogResult empty() {
        return new WikidataCatalogResult(null, List.of());
    }
}
