package de.travelmate.trip;

import de.travelmate.catalog.AttractionCatalogResponse;
import de.travelmate.catalog.AttractionCatalogService;
import de.travelmate.planning.PlanningService;
import de.travelmate.interest.InterestRepository;
import de.travelmate.interest.InterestType;
import de.travelmate.interest.InterestEntity;
import de.travelmate.user.CurrentUserService;
import de.travelmate.user.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TripService {
    @Inject
    CurrentUserService currentUser;

    @Inject
    TripRepository trips;

    @Inject
    PlanningService planning;

    @Inject
    TripScheduleService scheduleService;

    @Inject
    TripTimeWindowPolicy timeWindowPolicy;

    @Inject
    AttractionCatalogService catalog;

    @Inject
    TripDateValidator dateValidator;

    @Inject
    InterestRepository interests;

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

        List<InterestEntity> selected = resolveSelectedInterests(request);
        if (selected.isEmpty()) {
            throw new BadRequestException("Mindestens ein Interesse ist erforderlich.");
        }
        trip.selectedInterests = new java.util.HashSet<>(selected);
        trips.persist(trip);
        List<Long> interestIds = selected.stream().map(interest -> interest.id).toList();
        Set<InterestType> selectedTypes = selected.stream()
            .map(interest -> InterestType.valueOf(interest.code))
            .collect(java.util.stream.Collectors.toSet());
        timeWindowPolicy().extendDaysForInterests(trip, selectedTypes);
        planning.generatePlan(trip, interestIds, selectedTypes);
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
        List<InterestEntity> selected;
        if (interestIds == null || interestIds.isEmpty()) {
            selected = trip.selectedInterests.isEmpty()
                ? currentUser.requireCurrentUser().interests.stream().toList()
                : trip.selectedInterests.stream().toList();
        } else {
            selected = interests.findByIds(interestIds);
        }
        trip.selectedInterests = new java.util.HashSet<>(selected);
        interestIds = selected.stream().map(interest -> interest.id).toList();
        Set<InterestType> selectedTypes = selected.stream()
            .map(interest -> InterestType.valueOf(interest.code))
            .collect(java.util.stream.Collectors.toSet());
        timeWindowPolicy().extendDaysForInterests(trip, selectedTypes);
        planning.generatePlan(trip, interestIds, selectedTypes);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto deleteActivity(Long tripId, Long dayId, Long itemId) {
        TripEntity trip = requireMine(tripId);
        scheduleService.deleteActivity(trip, dayId, itemId);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateAvailability(Long tripId, Long dayId, UpdateDayAvailabilityRequest request) {
        TripEntity trip = requireMine(tripId);
        scheduleService.updateAvailability(trip, dayId, request);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto updateSchedule(Long tripId, UpdateScheduleRequest request) {
        TripEntity trip = requireMine(tripId);
        scheduleService.updateSchedule(trip, request);
        return TripDto.from(requireMine(tripId));
    }

    @Transactional
    public TripDto regenerateActivity(Long tripId, Long dayId, Long itemId) {
        TripEntity trip = requireMine(tripId);
        scheduleService.regenerateActivity(trip, dayId, itemId);
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
        scheduleService.replaceActivity(trip, dayId, itemId, request);
        return TripDto.from(trip);
    }

    @Transactional
    public TripDto addActivity(Long tripId, Long dayId, ReplaceTripActivityRequest request) {
        TripEntity trip = requireMine(tripId);
        scheduleService.addActivity(trip, dayId, request);
        return TripDto.from(trip);
    }

    @Transactional
    public AttractionCatalogResponse catalogAttractions(Long tripId) {
        return catalog.listForTrip(requireMine(tripId));
    }

    @Transactional
    public TripDto addCatalogAttraction(Long tripId, Long dayId, String catalogId) {
        TripEntity trip = requireMine(tripId);
        catalog.addToDay(trip, dayId, catalogId);
        return TripDto.from(trip);
    }

    private TripEntity requireMine(Long tripId) {
        return trips.findForUser(tripId, currentUser.requireCurrentUser())
            .orElseThrow(() -> new NotFoundException("Reise nicht gefunden."));
    }

    private String normalizeCity(String city) {
        String trimmed = city.trim();
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }

    private List<InterestEntity> resolveSelectedInterests(CreateTripRequest request) {
        if (request.interestIds() != null && !request.interestIds().isEmpty()) {
            return interests.findByIds(request.interestIds());
        }
        Set<InterestType> requestedInterests = request.interests() == null ? Set.of() : Set.copyOf(request.interests());
        return interests.findByCodes(requestedInterests);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TripTimeWindowPolicy timeWindowPolicy() {
        return timeWindowPolicy == null ? new TripTimeWindowPolicy() : timeWindowPolicy;
    }

}
