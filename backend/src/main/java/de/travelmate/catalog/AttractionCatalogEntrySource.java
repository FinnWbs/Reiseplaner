package de.travelmate.catalog;

import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AttractionCatalogEntrySource {
    @Inject
    AttractionCatalogSeedRepository seeds;

    @Inject
    AttractionCatalogGenerationService generatedCatalog;

    public List<AttractionCatalogEntry> entriesForTrip(TripEntity trip) {
        List<AttractionCatalogEntry> seedEntries = seeds.findByCity(trip.city);
        if (!seedEntries.isEmpty()) {
            return seedEntries;
        }
        return generatedCatalog == null ? List.of() : generatedCatalog.findOrGenerate(trip);
    }
}
