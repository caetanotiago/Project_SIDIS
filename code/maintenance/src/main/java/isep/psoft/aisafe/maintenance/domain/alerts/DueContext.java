package isep.psoft.aisafe.maintenance.domain.alerts;

import java.time.LocalDate;

public record DueContext(
        String aircraftRegistration,
        String aircraftModel,
        LocalDate manufacturingDate,
        String templateId,
        String templateName,
        Integer flightHoursInterval,
        Integer calendarDaysInterval,
        LocalDate lastCompletionDate,
        Double flightHoursAtCompletion,
        double currentFlightHours,
        LocalDate today) {
}
