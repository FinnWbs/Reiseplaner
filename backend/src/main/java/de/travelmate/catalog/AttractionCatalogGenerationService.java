package de.travelmate.catalog;

import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AttractionCatalogGenerationService {
    private static final Logger LOG = Logger.getLogger(AttractionCatalogGenerationService.class);

    @Inject
    CatalogCityRepository cities;

    @Inject
    WikidataCatalogProvider wikidata;

    @Inject
    WikipediaPageviewService pageviews;

    @Inject
    AttractionCatalogScorer scorer;

    @Inject
    AttractionCatalogSettings settings;

    public List<AttractionCatalogEntry> findOrGenerate(TripEntity trip) {
        if (!settings.wikimediaEnabled()) {
            return List.of();
        }
        String cityKey = CatalogCityKey.from(trip.city);
        String countryCode = CatalogCityKey.countryCode(trip.countryCode);
        Optional<CatalogCityEntity> cached = cities.findLatest(
            cityKey,
            countryCode,
            AttractionCatalogSettings.SOURCE_VERSION
        );
        if (cached.isPresent()
            && cached.get().status != CatalogGenerationStatus.FAILED
            && cached.get().generatedAt.isAfter(freshAfter())) {
            return cached.get().attractions.stream().map(CatalogAttractionEntity::toEntry).toList();
        }

        try {
            WikidataCatalogResult generated = wikidata.catalogFor(trip);
            List<WikimediaCatalogCandidate> candidates = generated.candidates();
            pageviews.enrich(candidates);
            List<WikimediaCatalogCandidate> ranked = scorer.scoreAndRank(candidates, settings.maxItems());
            CatalogCityEntity city = cached.orElseGet(CatalogCityEntity::new);
            replaceCache(city, trip, cityKey, countryCode, generated.boundary(), ranked, null);
            if (city.id == null) {
                cities.persist(city);
            }
            return city.attractions.stream().map(CatalogAttractionEntity::toEntry).toList();
        } catch (RuntimeException exception) {
            LOG.warnf(
                "Wikimedia catalog generation failed: city=%s, exception=%s, reason=%s",
                trip.city,
                exception.getClass().getSimpleName(),
                safeReason(exception)
            );
            if (cached.isPresent() && !cached.get().attractions.isEmpty()) {
                return cached.get().attractions.stream().map(CatalogAttractionEntity::toEntry).toList();
            }
            CatalogCityEntity city = cached.orElseGet(CatalogCityEntity::new);
            replaceCache(
                city,
                trip,
                cityKey,
                countryCode,
                null,
                List.of(),
                "Wikimedia-Katalog konnte nicht erzeugt werden."
            );
            city.status = CatalogGenerationStatus.FAILED;
            if (city.id == null) {
                cities.persist(city);
            }
            return List.of();
        }
    }

    private LocalDateTime freshAfter() {
        return LocalDateTime.now().minusDays(Math.max(1, settings.cacheTtlDays()));
    }

    private static void replaceCache(
        CatalogCityEntity city,
        TripEntity trip,
        String cityKey,
        String countryCode,
        CityBoundary boundary,
        List<WikimediaCatalogCandidate> candidates,
        String failureMessage
    ) {
        city.cityKey = cityKey;
        city.cityName = trip.city;
        city.country = trip.country;
        city.countryCode = countryCode;
        city.wikidataId = boundary == null ? null : boundary.wikidataId();
        city.latitude = boundary == null ? trip.latitude : Double.valueOf(boundary.centerLat());
        city.longitude = boundary == null ? trip.longitude : Double.valueOf(boundary.centerLon());
        city.sourceVersion = AttractionCatalogSettings.SOURCE_VERSION;
        city.generatedAt = LocalDateTime.now();
        city.status = failureMessage == null
            ? (candidates.isEmpty() ? CatalogGenerationStatus.EMPTY : CatalogGenerationStatus.GENERATED)
            : CatalogGenerationStatus.FAILED;
        city.message = failureMessage;
        city.attractions.clear();

        int rank = 1;
        for (WikimediaCatalogCandidate candidate : candidates) {
            CatalogAttractionEntity attraction = new CatalogAttractionEntity();
            attraction.catalogCity = city;
            attraction.catalogId = candidate.catalogId;
            attraction.name = candidate.name;
            attraction.wikidataId = candidate.wikidataId;
            attraction.wikipediaProject = candidate.wikipediaProject;
            attraction.wikipediaTitle = candidate.wikipediaTitle;
            attraction.primaryInterest = candidate.primaryInterest;
            attraction.category = candidate.category;
            attraction.latitude = candidate.latitude;
            attraction.longitude = candidate.longitude;
            attraction.rank = rank++;
            attraction.description = candidate.description;
            attraction.publicAttractionScore = candidate.publicAttractionScore;
            attraction.pageviews = candidate.pageviews;
            attraction.sitelinkCount = candidate.sitelinkCount;
            attraction.source = AttractionCatalogSource.WIKIMEDIA;
            city.attractions.add(attraction);
        }
    }

    private static String safeReason(RuntimeException exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.isBlank()) {
            Throwable cause = exception.getCause();
            reason = cause == null ? exception.getClass().getSimpleName() : cause.getMessage();
        }
        return reason == null || reason.isBlank() ? "unbekannt" : reason;
    }
}
