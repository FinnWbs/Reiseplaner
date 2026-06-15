package de.travelmate.activity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ActivityExternalRefRepository implements PanacheRepository<ActivityExternalRefEntity> {
    public Optional<ActivityExternalRefEntity> findBySourceAndExternalId(ActivitySource source, String externalId) {
        return find("source = ?1 and externalId = ?2", source, externalId).firstResultOptional();
    }
}
