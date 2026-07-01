package de.travelmate.activity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ActivityRepository implements PanacheRepository<ActivityEntity> {
    public Optional<ActivityEntity> findBySourceAndExternalId(ActivitySource source, String externalId) {
        return find("source = ?1 and externalId = ?2", source, externalId).firstResultOptional();
    }

    public Optional<ActivityEntity> findByExternalReference(ActivitySource source, String externalId) {
        return find(
            "select distinct activity from ActivityEntity activity join activity.externalRefs ref "
                + "where ref.source = ?1 and ref.externalId = ?2",
            source,
            externalId
        ).firstResultOptional();
    }

    public Optional<ActivityEntity> findByNormalizedNameAndCity(String name, String city) {
        return find("lower(trim(name)) = ?1 and lower(trim(city)) = ?2", name, city).firstResultOptional();
    }

    public List<ActivityEntity> findByCity(String city) {
        return list("lower(city) = ?1 order by name", city.trim().toLowerCase());
    }

    public List<ActivityEntity> findActiveByCity(String city) {
        return list(
            "lower(city) = ?1 and active = true and importVersion = ?2 order by name",
            city.trim().toLowerCase(),
            ActivityPersistenceService.CURRENT_IMPORT_VERSION
        );
    }

    public boolean hasFreshMinimumForCity(String city, int minimum, LocalDateTime freshAfter) {
        long count = count("lower(city) = ?1 and lastSyncedAt >= ?2", city.toLowerCase(), freshAfter);
        return count >= minimum;
    }

    public long deactivateForCityAndInterest(String city, de.travelmate.interest.InterestType interest) {
        return update(
            "active = false where source = ?1 and lower(city) = ?2 and primaryInterest = ?3",
            ActivitySource.GEOAPIFY,
            city.trim().toLowerCase(),
            interest
        );
    }
}
