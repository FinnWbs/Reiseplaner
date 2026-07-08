package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttractionCatalogGenerationServiceTest {
    @Test
    void generationFailureWithMissingTripCoordinatesDoesNotThrow() {
        CapturingCatalogCityRepository cities = new CapturingCatalogCityRepository();
        AttractionCatalogSettings settings = new AttractionCatalogSettings();
        settings.wikimediaEnabled = true;
        settings.cacheTtlDays = 30;

        AttractionCatalogGenerationService service = new AttractionCatalogGenerationService();
        service.cities = cities;
        service.wikidata = new ThrowingWikidataCatalogProvider();
        service.pageviews = new WikipediaPageviewService();
        service.scorer = new AttractionCatalogScorer();
        service.settings = settings;

        TripEntity trip = new TripEntity();
        trip.city = "Rom";

        List<AttractionCatalogEntry> result = assertDoesNotThrow(() -> service.findOrGenerate(trip));

        assertEquals(List.of(), result);
        assertEquals(CatalogGenerationStatus.FAILED, cities.persisted.status);
        assertNull(cities.persisted.latitude);
        assertNull(cities.persisted.longitude);
    }

    @Test
    void failedFreshCacheIsRetried() {
        CatalogCityEntity failedCache = new CatalogCityEntity();
        failedCache.cityKey = "rom";
        failedCache.cityName = "Rom";
        failedCache.countryCode = "IT";
        failedCache.sourceVersion = AttractionCatalogSettings.SOURCE_VERSION;
        failedCache.status = CatalogGenerationStatus.FAILED;
        failedCache.generatedAt = LocalDateTime.now();

        CachedCatalogCityRepository cities = new CachedCatalogCityRepository(failedCache);
        AttractionCatalogSettings settings = new AttractionCatalogSettings();
        settings.wikimediaEnabled = true;
        settings.cacheTtlDays = 30;
        settings.maxItems = 15;

        AttractionCatalogGenerationService service = new AttractionCatalogGenerationService();
        service.cities = cities;
        service.wikidata = new SingleCandidateWikidataCatalogProvider();
        service.pageviews = new NoopPageviewService();
        service.scorer = new AttractionCatalogScorer();
        service.settings = settings;

        TripEntity trip = new TripEntity();
        trip.city = "Rom";
        trip.countryCode = "IT";

        List<AttractionCatalogEntry> result = service.findOrGenerate(trip);

        assertEquals(1, result.size());
        assertEquals(CatalogGenerationStatus.GENERATED, failedCache.status);
        assertTrue(failedCache.attractions.stream().anyMatch(entry -> "Kolosseum".equals(entry.name)));
    }

    static class CapturingCatalogCityRepository extends CatalogCityRepository {
        CatalogCityEntity persisted;

        @Override
        public Optional<CatalogCityEntity> findLatest(String cityKey, String countryCode, int sourceVersion) {
            return Optional.empty();
        }

        @Override
        public void persist(CatalogCityEntity entity) {
            persisted = entity;
        }
    }

    static class ThrowingWikidataCatalogProvider extends WikidataCatalogProvider {
        @Override
        WikidataCatalogResult catalogFor(TripEntity trip) {
            throw new IllegalStateException("simulierter Fehler");
        }
    }

    static class CachedCatalogCityRepository extends CatalogCityRepository {
        private final CatalogCityEntity cached;

        CachedCatalogCityRepository(CatalogCityEntity cached) {
            this.cached = cached;
        }

        @Override
        public Optional<CatalogCityEntity> findLatest(String cityKey, String countryCode, int sourceVersion) {
            return Optional.of(cached);
        }
    }

    static class SingleCandidateWikidataCatalogProvider extends WikidataCatalogProvider {
        @Override
        WikidataCatalogResult catalogFor(TripEntity trip) {
            WikimediaCatalogCandidate candidate = new WikimediaCatalogCandidate();
            candidate.catalogId = "rom-q10285";
            candidate.name = "Kolosseum";
            candidate.city = "Rom";
            candidate.wikidataId = "Q10285";
            candidate.wikipediaProject = "de.wikipedia.org";
            candidate.wikipediaTitle = "Kolosseum";
            candidate.primaryInterest = InterestType.SIGHTSEEING;
            candidate.category = "landmark";
            candidate.latitude = 41.8902;
            candidate.longitude = 12.4922;
            candidate.description = "Amphitheater in Rom";
            candidate.sitelinkCount = 300;
            candidate.hasCoordinates = true;
            candidate.categoryFitScore = 0.95;
            CityBoundary boundary = CityBoundary.fallback("Rom", "Q220", 41.9028, 12.4964, 12, 1.5, 3);
            return new WikidataCatalogResult(boundary, List.of(candidate));
        }
    }

    static class NoopPageviewService extends WikipediaPageviewService {
        @Override
        public void enrich(List<WikimediaCatalogCandidate> candidates) {
            candidates.forEach(candidate -> candidate.pageviews = 100_000);
        }
    }
}
