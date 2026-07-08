package de.travelmate.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.InternalServerErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AttractionCatalogSeedRepository {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<AttractionCatalogEntry>> cache = new ConcurrentHashMap<>();

    public List<AttractionCatalogEntry> findByCity(String city) {
        String cityKey = CatalogCityKey.from(city);
        return cache.computeIfAbsent(cityKey, this::loadCity);
    }

    public Optional<AttractionCatalogEntry> findByCityAndCatalogId(String city, String catalogId) {
        return findByCity(city).stream()
            .filter(entry -> entry.catalogId().equals(catalogId))
            .findFirst();
    }

    private List<AttractionCatalogEntry> loadCity(String cityKey) {
        String path = "attraction-catalog/" + cityKey + ".json";
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return List.of();
            }
            List<AttractionCatalogEntry> entries =
                mapper.readValue(stream, new TypeReference<List<AttractionCatalogEntry>>() {});
            return entries.stream()
                .map(this::withSeedDefaults)
                .sorted(java.util.Comparator.comparingInt(AttractionCatalogEntry::rank))
                .toList();
        } catch (IOException exception) {
            throw new InternalServerErrorException("Highlight-Katalog konnte nicht geladen werden.", exception);
        }
    }

    private AttractionCatalogEntry withSeedDefaults(AttractionCatalogEntry entry) {
        return new AttractionCatalogEntry(
            entry.catalogId(),
            entry.name(),
            entry.city(),
            entry.wikidataId(),
            entry.wikipediaProject(),
            entry.wikipediaTitle(),
            entry.primaryInterest(),
            entry.category(),
            entry.latitude(),
            entry.longitude(),
            entry.rank(),
            entry.description(),
            entry.publicAttractionScore(),
            entry.pageviews(),
            entry.sitelinkCount(),
            entry.source() == null || entry.source().isBlank() ? AttractionCatalogSource.SEED.name() : entry.source()
        );
    }
}
