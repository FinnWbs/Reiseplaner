package de.travelmate.activity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ActivityImageRepository implements PanacheRepository<ActivityImageEntity> {
    public List<ActivityImageEntity> findForActivity(Long activityId) {
        return list("activity.id = ?1 order by sortOrder, id", activityId);
    }
}
