package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for completing a maintenance intervention.
 *
 * @author Diogo Fernandes - 1240632
 */
public record CompleteMaintenanceRequest(
        @NotNull LocalDate completionDate,
        @NotNull @Valid MaintenanceReportRequest report) {
}
