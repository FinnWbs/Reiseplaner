package de.travelmate.feedback;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepository<FeedbackEntity> {}
