package isep.psoft.aisafe.maintenance.dto;

import isep.psoft.aisafe.maintenance.domain.Maintenance;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;

import java.time.LocalDate;

/**
 * HTTP representation of a maintenance record, including responses forwarded between replicas.
 *
 * @author Diogo Fernandes - 1240632
 */
public record MaintenanceRecordDTO(
        String id,
        String aircraftRegistration,
        LocalDate scheduledDate,
        LocalDate completionDate,
        String serviceType,
        String report,
        MaintenanceStatus status) {

    public static MaintenanceRecordDTO from(Maintenance maintenance) {
        return new MaintenanceRecordDTO(
                maintenance.getId(),
                maintenance.getAircraftRegistration(),
                maintenance.getScheduledDate(),
                maintenance.getCompletionDate(),
                maintenance.getServiceType(),
                maintenance.getReport() == null ? null : maintenance.getReport().getText(),
                maintenance.getStatus());
    }
}
