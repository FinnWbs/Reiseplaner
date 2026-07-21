package de.travelmate.interest;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class InterestRepository implements PanacheRepository<InterestEntity> {
    public List<InterestEntity> findByIds(List<Long> ids) {
        return list("id in ?1", ids);
    }

    public List<InterestEntity> findByCodes(Set<InterestType> codes) {
        return codes.isEmpty() ? List.of() : list("code in ?1", codes.stream().map(Enum::name).toList());
    }

    public Optional<InterestEntity> findByCode(InterestType code) {
        return find("code", code.name()).firstResultOptional();
    }
}
