package de.travelmate.trip;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.planning.PlanningService;
import de.travelmate.user.CurrentUserService;
import de.travelmate.user.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class TripService {
    @Inject
    CurrentUserService currentUser;

    @Inject
    TripRepository trips;

    @Inject
    ActivityRepository activities;

    @Inject
    TripDayActivityRepository tripActivities;

    @Inject
    PlanningService planning;

    @Transactional
    public TripDto create(CreateTripRequest request) {
        UserEntity user = currentUser.requireCurrentUser();
        TripEntity trip = new TripEntity();
        trip.user = user;
        trip.city = normalizeCity(request.city());
        trip.daysCount = request.daysCount();

        for (int dayNumber = 1; dayNumber <= trip.daysCount; dayNumber++) {
            TripDayEntity day = new TripDayEntity();
            day.trip = trip;
            day.dayNumber = dayNumber;
            trip.days.add(day);
        }

        trips.persist(trip);
        planning.generatePlan(trip, request.interestIds());
        return TripDto.from(trip);
    }

    @Transactional
    public List<TripDto> listMine() {
        return trips.findForUser(currentUser.requireCurrentUser()).stream().map(TripDto::from).toList();
    }

    @Transactional
    public TripDto getMine(Long tripId) {
        return TripDto.from(requireMine(tripId));
    }

    @Transactional
    public TripDto generatePlan(Long tripId, GeneratePlanRequest request) {
        TripEntity trip = requireMine(tripId);
        List<Long> interestIds = request == null ? List.of() : request.interestIds();
        if (interestIds == null || interestIds.isEmpty()) {
            interestIds = currentUser.requireCurrentUser().interests.stream().map(interest -> interest.id).toList();
        }
        planning.generatePlan(trip, interestIds);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto deleteActivity(Long tripId, Long dayId, Long itemId) {
        TripEntity trip = requireMine(tripId);
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        day.activities.remove(item);
        tripActivities.delete(item);
        renumber(day);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto replaceActivity(Long tripId, Long dayId, Long itemId, ReplaceTripActivityRequest request) {
        TripEntity trip = requireMine(tripId);
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        ActivityEntity replacement = activities.findByIdOptional(request.activityId())
            .orElseThrow(() -> new BadRequestException("Aktivitaet existiert nicht."));
        item.activity = replacement;
        item.notes = request.notes();
        item.locked = request.locked() != null && request.locked();
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto addActivity(Long tripId, Long dayId, ReplaceTripActivityRequest request) {
        TripEntity trip = requireMine(tripId);
        TripDayEntity day = requireDay(trip, dayId);
        if (day.activities.size() >= 2) {
            throw new BadRequestException("Im MVP sind maximal zwei Aktivitaeten pro Tag vorgesehen.");
        }
        ActivityEntity activity = activities.findByIdOptional(request.activityId())
            .orElseThrow(() -> new BadRequestException("Aktivitaet existiert nicht."));
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.tripDay = day;
        item.activity = activity;
        item.position = day.activities.size() + 1;
        item.notes = request.notes();
        item.locked = request.locked() != null && request.locked();
        day.activities.add(item);
        return TripDto.from(trip);
    }

    private TripEntity requireMine(Long tripId) {
        return trips.findForUser(tripId, currentUser.requireCurrentUser())
            .orElseThrow(() -> new NotFoundException("Reise nicht gefunden."));
    }

    private TripDayEntity requireDay(TripEntity trip, Long dayId) {
        return trip.days.stream()
            .filter(day -> day.id.equals(dayId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Reisetag nicht gefunden."));
    }

    private TripDayActivityEntity requireItem(TripDayEntity day, Long itemId) {
        return day.activities.stream()
            .filter(item -> item.id.equals(itemId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Planeintrag nicht gefunden."));
    }

    private void renumber(TripDayEntity day) {
        for (int i = 0; i < day.activities.size(); i++) {
            day.activities.get(i).position = i + 1;
        }
    }

    private String normalizeCity(String city) {
        String trimmed = city.trim();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }
}
