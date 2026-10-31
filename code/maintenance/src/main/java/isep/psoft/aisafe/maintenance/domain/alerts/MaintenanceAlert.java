package isep.psoft.aisafe.maintenance.domain.alerts;

import java.time.LocalDate;

/**
 * Read model (não persistido): uma aeronave atingiu o limite de um template periódico (US222).
 *
 * @param limit   intervalo do template (horas ou dias)
 * @param current horas de voo ou dias decorridos desde a última conclusão
 */
public record MaintenanceAlert(
        String aircraftRegistration,
        String aircraftModel,
        String templateId,
        String templateName,
        DueTrigger trigger,
        double limit,
        double current,
        LocalDate lastCompletionDate) {

    public String message() {
        String unit = trigger == DueTrigger.FLIGHT_HOURS ? "flight hours" : "days";
        String since;
        if (lastCompletionDate != null) {
            since = "since it was last performed on " + lastCompletionDate;
        } else {
            since = trigger == DueTrigger.FLIGHT_HOURS ? "in total (never performed)" : "since manufacture (never performed)";
        }
        return String.format(java.util.Locale.ROOT, "Aircraft %s is due for '%s': %.0f %s %s (limit %.0f).",
                aircraftRegistration, templateName, current, unit, since, limit);
    }
}
