package isep.psoft.aisafe.maintenance.domain.alerts;

import java.time.LocalDate;

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
