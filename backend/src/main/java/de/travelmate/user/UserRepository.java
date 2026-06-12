package de.travelmate.user;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<UserEntity> {
    public Optional<UserEntity> findByEmail(String email) {
        return find("lower(email)", email.toLowerCase()).firstResultOptional();
    }
}
