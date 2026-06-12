package de.travelmate.interest;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class InterestRepository implements PanacheRepository<InterestEntity> {
    public List<InterestEntity> findByIds(List<Long> ids) {
        return list("id in ?1", ids);
    }
}
