package de.travelmate.trip;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class TripDateValidator {
    public List<LocalDate> validate(
        LocalDate startDate,
        LocalDate endDate,
        List<LocalDate> requestedDates
    ) {
        if ((startDate == null) != (endDate == null)) {
            throw new BadRequestException("Ankunft und Abreise muessen gemeinsam angegeben werden.");
        }
        if (startDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("Die Abreise darf nicht vor der Ankunft liegen.");
        }
        List<LocalDate> dates = requestedDates == null
            ? List.of()
            : requestedDates.stream().distinct().sorted().toList();
        if (!dates.isEmpty() && startDate == null) {
            throw new BadRequestException("Planungstage benoetigen einen Reisezeitraum.");
        }
        if (startDate != null && dates.isEmpty()) {
            throw new BadRequestException("Mindestens ein Planungstag ist erforderlich.");
        }
        if (dates.stream().anyMatch(date -> date.isBefore(startDate) || date.isAfter(endDate))) {
            throw new BadRequestException("Planungstage muessen innerhalb des Reisezeitraums liegen.");
        }
        if (dates.size() > 14) {
            throw new BadRequestException("Maximal 14 Planungstage sind erlaubt.");
        }
        return dates;
    }
}
