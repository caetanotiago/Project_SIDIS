package isep.psoft.aisafe.maintenance.domain.alerts;

import java.time.LocalDate;

/**
 * Dados necessários para decidir se um template periódico está em atraso numa aeronave (US222).
 *
 * @param lastCompletionDate      última conclusão deste template nesta aeronave (null = nunca)
 * @param flightHoursAtCompletion horas de voo registadas nessa conclusão (null = não registadas)
 * @param currentFlightHours      horas de voo atuais da aeronave (US206)
 */
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
