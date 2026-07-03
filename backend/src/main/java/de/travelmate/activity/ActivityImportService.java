package de.travelmate.activity;

import de.travelmate.datasource.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import de.travelmate.interest.InterestType;
import de.travelmate.quality.PoiRelationshipService;

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

    @Inject
    PoiRelationshipService relationships;

    public ActivityImportResponse importCity(String requestedCity) {
        String city = normalizeCity(requestedCity);
        return importCity(city, city);
    }

    public ActivityImportResponse importCity(String requestedCity, String lookupText) {
        return importCity(requestedCity, lookupText, null, null, null, InterestType.primaryTypes());
    }

    public ActivityImportResponse importCity(
        String requestedCity,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude
    ) {
        return importCity(requestedCity, lookupText, placeId, latitude, longitude, InterestType.primaryTypes());
    }

    public ActivityImportResponse importCity(
        String requestedCity,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude,
        Set<InterestType> interests
    ) {
        String city = normalizeCity(requestedCity);
        String externalLookup = lookupText == null || lookupText.isBlank() ? city : lookupText.trim();
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        List<ExternalActivityCandidate> candidates = new ArrayList<>();
        for (InterestType interest : selected) {
            candidates.addAll(geoapify.fetch(externalLookup, placeId, latitude, longitude, Set.of(interest)));
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(wikidata.enrich(candidates));
        warnings.addAll(wikipedia.enrich(candidates));
        if (openStreetMap.isEnabled()) {
            candidates.addAll(openStreetMap.fetch(externalLookup));
        }
        relationships.suppressSubPois(candidates);
        return persistence.persist(city, candidates, warnings);
    }

    public ActivityImportResponse importInterest(
        String requestedCity,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude,
        InterestType interest
    ) {
        return importInterest(requestedCity, lookupText, placeId, latitude, longitude, interest, null);
    }

    public ActivityImportResponse importInterest(
        String requestedCity,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude,
        InterestType interest,
        ImportDemand demand
    ) {
        String city = normalizeCity(requestedCity);
        String externalLookup = lookupText == null || lookupText.isBlank() ? city : lookupText.trim();
        List<ExternalActivityCandidate> candidates =
            new ArrayList<>(geoapify.fetch(externalLookup, placeId, latitude, longitude, Set.of(interest), demand));
        List<String> warnings = new ArrayList<>();
        warnings.addAll(wikidata.enrich(candidates));
        warnings.addAll(wikipedia.enrich(candidates));
        relationships.suppressSubPois(candidates);
        persistence.deactivateGeoapifyActivities(city, interest);
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
