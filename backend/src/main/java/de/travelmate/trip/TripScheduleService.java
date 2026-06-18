package de.travelmate.trip;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.planning.ActivityTimeRules;
import de.travelmate.planning.PlanningService;
import de.travelmate.user.CurrentUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class TripScheduleService {
    private static final int TEMPORARY_POSITION_OFFSET = 100_000;
    private static final int STOP_GAP_MINUTES = 30;

    @Inject
    CurrentUserService currentUser;

    @Inject
    ActivityRepository activities;

    @Inject
    TripDayActivityRepository tripActivities;

    @Inject
    PlanningService planning;

    @Inject
    ActivityTimeRules timeRules;

    public void deleteActivity(TripEntity trip, Long dayId, Long itemId) {
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        tripActivities.delete(item);
        tripActivities.flush();
        day.activities.remove(item);
        day.activities.sort(Comparator.comparingInt(activity -> activity.position));
        renumber(day);
    }

    public void updateAvailability(TripEntity trip, Long dayId, UpdateDayAvailabilityRequest request) {
        TripDayEntity day = requireDay(trip, dayId);
        if (request.availableFrom() >= request.availableUntil()) {
            throw new BadRequestException("Das Zeitfenster muss mindestens 30 Minuten lang sein.");
        }
        day.availableFrom = request.availableFrom();
        day.availableUntil = request.availableUntil();
    }

    public TripEntity updateSchedule(TripEntity trip, UpdateScheduleRequest request) {
        Map<Long, TripDayEntity> dayById = new HashMap<>();
        Map<Long, TripDayActivityEntity> itemById = new HashMap<>();
        for (TripDayEntity day : trip.days) {
            dayById.put(day.id, day);
            day.activities.forEach(item -> itemById.put(item.id, item));
        }

        List<Long> requestedItems = validateScheduleRequest(request, dayById, itemById);
        moveItemsToTemporaryPositivePositions(requestedItems);
        persistRequestedSchedule(request, dayById, itemById);
        tripActivities.getEntityManager().flush();
        tripActivities.getEntityManager().clear();
        return trip;
    }

    public void regenerateActivity(TripEntity trip, Long dayId, Long itemId) {
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        Set<Long> interestIds = currentUser.requireCurrentUser().interests.stream()
            .map(interest -> interest.id)
            .collect(java.util.stream.Collectors.toSet());
        ActivityEntity replacement = planning.replacementFor(trip, item, interestIds)
            .orElseThrow(() -> new BadRequestException("Keine weitere passende Aktivitaet verfuegbar."));
        item.activity = replacement;
        item.locked = true;
    }

    public void replaceActivity(TripEntity trip, Long dayId, Long itemId, ReplaceTripActivityRequest request) {
        TripDayEntity day = requireDay(trip, dayId);
        TripDayActivityEntity item = requireItem(day, itemId);
        ActivityEntity replacement = activities.findByIdOptional(request.activityId())
            .orElseThrow(() -> new BadRequestException("Aktivitaet existiert nicht."));
        item.activity = replacement;
        item.notes = request.notes();
        item.locked = request.locked() != null && request.locked();
    }

    public void addActivity(TripEntity trip, Long dayId, ReplaceTripActivityRequest request) {
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
    }

    private List<Long> validateScheduleRequest(
        UpdateScheduleRequest request,
        Map<Long, TripDayEntity> dayById,
        Map<Long, TripDayActivityEntity> itemById
    ) {
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
        return requestedItems;
    }

    private void moveItemsToTemporaryPositivePositions(List<Long> requestedItems) {
        for (int index = 0; index < requestedItems.size(); index++) {
            tripActivities.getEntityManager().createNativeQuery(
                "UPDATE trip_day_activities SET position = ?1 WHERE id = ?2"
            ).setParameter(1, temporaryPositionFor(index))
                .setParameter(2, requestedItems.get(index))
                .executeUpdate();
        }
        tripActivities.flush();
    }

    static int temporaryPositionFor(int index) {
        return TEMPORARY_POSITION_OFFSET + index;
    }

    private void persistRequestedSchedule(
        UpdateScheduleRequest request,
        Map<Long, TripDayEntity> dayById,
        Map<Long, TripDayActivityEntity> itemById
    ) {
        for (ScheduleDayRequest dayRequest : request.days()) {
            TripDayEntity day = dayById.get(dayRequest.dayId());
            int cursor = day.availableFrom;
            for (int index = 0; index < dayRequest.activityItemIds().size(); index++) {
                TripDayActivityEntity item = itemById.get(dayRequest.activityItemIds().get(index));
                int start = nextStart(day, item, cursor);
                tripActivities.getEntityManager().createNativeQuery(
                    "UPDATE trip_day_activities "
                        + "SET trip_day_id = ?1, position = ?2, scheduled_start = ?3, locked = TRUE "
                        + "WHERE id = ?4"
                ).setParameter(1, day.id)
                    .setParameter(2, index + 1)
                    .setParameter(3, start)
                    .setParameter(4, item.id)
                    .executeUpdate();
                cursor = start + item.durationMinutes + STOP_GAP_MINUTES;
            }
        }
    }

    private int nextStart(TripDayEntity day, TripDayActivityEntity item, int cursor) {
        ActivityTimeRules.TimeProfile profile = timeRules.profile(item.activity);
        int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
        if (start < profile.preferredStart()
            && profile.preferredStart() + item.durationMinutes <= day.availableUntil) {
            start = profile.preferredStart();
        }
        return Math.min(start, 1440 - item.durationMinutes);
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
}
