package de.travelmate.trip;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TripDayActivityRepository implements PanacheRepository<TripDayActivityEntity> {}
