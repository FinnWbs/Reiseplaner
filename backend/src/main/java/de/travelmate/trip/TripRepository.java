package de.travelmate.trip;

import de.travelmate.user.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TripRepository implements PanacheRepository<TripEntity> {
    public List<TripEntity> findForUser(UserEntity user) {
        return list("user = ?1 order by createdAt desc", user);
    }

    public Optional<TripEntity> findForUser(Long tripId, UserEntity user) {
        return find("id = ?1 and user = ?2", tripId, user).firstResultOptional();
    }
}
