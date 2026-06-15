package de.travelmate.trip;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.planning.ActivityTimeRules;
import de.travelmate.planning.PlanningService;
import de.travelmate.user.CurrentUserService;
import de.travelmate.user.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Inject
    ActivityTimeRules timeRules;

    @Inject
    TripDateValidator dateValidator;

    @Transactional
    public TripDto create(CreateTripRequest request) {
        UserEntity user = currentUser.requireCurrentUser();
        TripEntity trip = new TripEntity();
        trip.user = user;
        trip.city = normalizeCity(request.city());
        trip.country = blankToNull(request.country());
        trip.countryCode = blankToNull(request.countryCode());
        trip.state = blankToNull(request.state());
        trip.latitude = request.latitude();
        trip.longitude = request.longitude();
        trip.placeId = blankToNull(request.placeId());
        trip.startDate = request.startDate();
        trip.endDate = request.endDate();
        trip.pace = request.pace() == null ? TripPace.BALANCED : request.pace();
        trip.dayRhythm = request.dayRhythm() == null ? DayRhythm.BALANCED : request.dayRhythm();
        trip.destinationSource = request.destinationSource() == null
            ? DestinationSource.KNOWN
            : request.destinationSource();

        List<LocalDate> planningDates = dateValidator.validate(
            request.startDate(),
            request.endDate(),
            request.planningDates()
        );
        trip.daysCount = planningDates.isEmpty() ? request.daysCount() : planningDates.size();

        for (int dayNumber = 1; dayNumber <= trip.daysCount; dayNumber++) {
            TripDayEntity day = new TripDayEntity();
            day.trip = trip;
            day.dayNumber = dayNumber;
            day.travelDate = planningDates.isEmpty() ? null : planningDates.get(dayNumber - 1);
            day.availableFrom = trip.dayRhythm.availableFrom();
            day.availableUntil = trip.dayRhythm.availableUntil();
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
    public void delete(Long tripId) {
        TripEntity trip = requireMine(tripId);
        trips.delete(trip);
        trips.flush();
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
        tripActivities.delete(item);
        tripActivities.flush();
        day.activities.remove(item);
        day.activities.sort(Comparator.comparingInt(activity -> activity.position));
        renumber(day);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateAvailability(Long tripId, Long dayId, UpdateDayAvailabilityRequest request) {
        TripEntity trip = requireMine(tripId);
        TripDayEntity day = requireDay(trip, dayId);
        if (request.availableFrom() >= request.availableUntil()) {
            throw new BadRequestException("Das Zeitfenster muss mindestens 30 Minuten lang sein.");
        }
        day.availableFrom = request.availableFrom();
        day.availableUntil = request.availableUntil();
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateSchedule(Long tripId, UpdateScheduleRequest request) {
        TripEntity trip = requireMine(tripId);
        Map<Long, TripDayEntity> dayById = new HashMap<>();
        Map<Long, TripDayActivityEntity> itemById = new HashMap<>();
        for (TripDayEntity day : trip.days) {
            dayById.put(day.id, day);
            day.activities.forEach(item -> itemById.put(item.id, item));
        }

        Set<Long> requestedDays = new HashSet<>();
        List<Long> requestedItems = new ArrayList<>();
        for (ScheduleDayRequest dayRequest : request.days()) {
            if (!dayById.containsKey(dayRequest.dayId()) || !requestedDays.add(dayRequest.dayId())) {
                throw new BadRequestException("Der Zeitplan enthaelt einen ungueltigen Reisetag.");
            }
            requestedItems.addAll(dayRequest.activityItemIds());
        }
        if (requestedItems.size() != new HashSet<>(requestedItems).size()
            || !new HashSet<>(requestedItems).equals(itemById.keySet())) {
            throw new BadRequestException("Jeder Planeintrag muss im Zeitplan genau einmal vorkommen.");
        }

        int temporaryPosition = -1;
        for (Long itemId : requestedItems) {
            tripActivities.getEntityManager().createNativeQuery(
                "UPDATE trip_day_activities SET position = ?1 WHERE id = ?2"
            ).setParameter(1, temporaryPosition--)
                .setParameter(2, itemId)
                .executeUpdate();
        }
        tripActivities.flush();

        for (ScheduleDayRequest dayRequest : request.days()) {
            TripDayEntity day = dayById.get(dayRequest.dayId());
            int cursor = day.availableFrom;
            for (int index = 0; index < dayRequest.activityItemIds().size(); index++) {
                TripDayActivityEntity item = itemById.get(dayRequest.activityItemIds().get(index));
                ActivityTimeRules.TimeProfile profile = timeRules.profile(item.activity);
                int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
                if (start < profile.preferredStart()
                    && profile.preferredStart() + item.durationMinutes <= day.availableUntil) {
                    start = profile.preferredStart();
                }
                start = Math.min(start, 1440 - item.durationMinutes);
                tripActivities.getEntityManager().createNativeQuery(
                    "UPDATE trip_day_activities "
                        + "SET trip_day_id = ?1, position = ?2, scheduled_start = ?3, locked = TRUE "
                        + "WHERE id = ?4"
                ).setParameter(1, day.id)
                    .setParameter(2, index + 1)
                    .setParameter(3, start)
                    .setParameter(4, item.id)
                    .executeUpdate();
                cursor = start + item.durationMinutes + 30;
            }
        }
        tripActivities.getEntityManager().flush();
        tripActivities.getEntityManager().clear();
        return TripDto.from(requireMine(tripId));
    }

    @Transactional
    public TripDto regenerateActivity(Long tripId, Long dayId, Long itemId) {
        TripEntity trip = requireMine(tripId);
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        Set<Long> interestIds = currentUser.requireCurrentUser().interests.stream()
            .map(interest -> interest.id)
            .collect(java.util.stream.Collectors.toSet());
        ActivityEntity replacement = planning.replacementFor(trip, item, interestIds)
            .orElseThrow(() -> new BadRequestException("Keine weitere passende Aktivitaet verfuegbar."));
        item.activity = replacement;
        item.locked = true;
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateDates(Long tripId, UpdateTripDatesRequest request) {
        TripEntity trip = requireMine(tripId);
        List<LocalDate> planningDates = dateValidator.validate(
            request.startDate(),
            request.endDate(),
            request.planningDates()
        );

        trip.startDate = request.startDate();
        trip.endDate = request.endDate();
        while (trip.days.size() > planningDates.size()) {
            trip.days.remove(trip.days.size() - 1);
        }
        while (trip.days.size() < planningDates.size()) {
            TripDayEntity day = new TripDayEntity();
            day.trip = trip;
            day.availableFrom = trip.dayRhythm.availableFrom();
            day.availableUntil = trip.dayRhythm.availableUntil();
            trip.days.add(day);
        }
        for (int index = 0; index < trip.days.size(); index++) {
            TripDayEntity day = trip.days.get(index);
            day.dayNumber = index + 1;
            day.travelDate = planningDates.get(index);
        }
        trip.daysCount = planningDates.size();
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
        if (day.activities.size() >= trip.pace.activitiesPerDay()) {
            throw new BadRequestException("Dieser Reisetag hat bereits die maximale Anzahl Aktivitaeten.");
        }
        ActivityEntity activity = activities.findByIdOptional(request.activityId())
            .orElseThrow(() -> new BadRequestException("Aktivitaet existiert nicht."));
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.tripDay = day;
        item.activity = activity;
        item.position = day.activities.size() + 1;
        ActivityTimeRules.TimeProfile profile = timeRules.profile(activity);
        item.durationMinutes = profile.durationMinutes();
        item.scheduledStart = Math.max(day.availableFrom, profile.earliestStart());
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
