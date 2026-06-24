package de.travelmate.sync;

import de.travelmate.interest.InterestType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class ActivityImportStateRepository implements PanacheRepository<ActivityImportStateEntity> {
    public Optional<ActivityImportStateEntity> findForCityAndInterest(String city, InterestType interest) {
        return find("lower(city) = ?1 and interest = ?2", city.trim().toLowerCase(), interest)
            .firstResultOptional();
    }

    public boolean isFresh(String city, InterestType interest, int version, LocalDateTime freshAfter) {
        return findForCityAndInterest(city, interest)
            .filter(state -> state.importVersion == version && !state.syncedAt.isBefore(freshAfter))
            .isPresent();
    }
}
