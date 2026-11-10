package isep.psoft.aisafe.maintenance.dto;

import isep.psoft.aisafe.maintenance.domain.alerts.MaintenanceAlert;

import java.time.LocalDate;

public record MaintenanceAlertDTO(String aircraftRegistration, String aircraftModel, String templateId,
                                  String templateName, String trigger, double limit, double current,
                                  LocalDate lastCompletionDate, String message) {

    public static MaintenanceAlertDTO from(MaintenanceAlert alert) {
        return new MaintenanceAlertDTO(alert.aircraftRegistration(), alert.aircraftModel(), alert.templateId(),
                alert.templateName(), alert.trigger().name(), alert.limit(), alert.current(),
                alert.lastCompletionDate(), alert.message());
    }
}
