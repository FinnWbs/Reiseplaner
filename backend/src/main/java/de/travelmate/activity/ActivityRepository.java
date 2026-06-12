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

    public List<ActivityEntity> findByCity(String city) {
        return list("lower(city)", city.toLowerCase());
    }

    public boolean hasFreshMinimumForCity(String city, int minimum, LocalDateTime freshAfter) {
        long count = count("lower(city) = ?1 and lastSyncedAt >= ?2", city.toLowerCase(), freshAfter);
        return count >= minimum;
    }
}
