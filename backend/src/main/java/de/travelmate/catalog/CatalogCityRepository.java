package de.travelmate.catalog;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class CatalogCityRepository implements PanacheRepository<CatalogCityEntity> {
    public Optional<CatalogCityEntity> findLatest(String cityKey, String countryCode, int sourceVersion) {
        return find(
            "cityKey = ?1 and countryCode = ?2 and sourceVersion = ?3 order by generatedAt desc",
            cityKey,
            countryCode,
            sourceVersion
        ).firstResultOptional();
    }
}
