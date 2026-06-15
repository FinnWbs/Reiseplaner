package de.travelmate.activity;

import de.travelmate.datasource.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class ActivityImportService {
    @Inject
    GeoapifyActivityProvider geoapify;

    @Inject
    WikidataActivityProvider wikidata;

    @Inject
    WikipediaActivityProvider wikipedia;

    @Inject
    OpenStreetMapActivityProvider openStreetMap;

    @Inject
    ActivityPersistenceService persistence;

    public ActivityImportResponse importCity(String requestedCity) {
        String city = normalizeCity(requestedCity);
        return importCity(city, city);
    }

    public ActivityImportResponse importCity(String requestedCity, String lookupText) {
        return importCity(requestedCity, lookupText, null, null, null);
    }

    public ActivityImportResponse importCity(
        String requestedCity,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude
    ) {
        String city = normalizeCity(requestedCity);
        String externalLookup = lookupText == null || lookupText.isBlank() ? city : lookupText.trim();
        List<ExternalActivityCandidate> candidates =
            new ArrayList<>(geoapify.fetch(externalLookup, placeId, latitude, longitude));
        if (candidates.isEmpty()) {
            throw new ExternalProviderException("Geoapify hat keine importierbaren Aktivitaeten geliefert.");
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(wikidata.enrich(candidates));
        warnings.addAll(wikipedia.enrich(candidates));
        if (openStreetMap.isEnabled()) {
            candidates.addAll(openStreetMap.fetch(externalLookup));
        }
        return persistence.persist(city, candidates, warnings);
    }

    public static String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            throw new BadRequestException("Stadt ist erforderlich.");
        }
        String trimmed = city.trim().replaceAll("\\s+", " ");
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }
}
