package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload containing the text of a maintenance completion report.
 *
 * @author Diogo Fernandes - 1240632
 */
public record MaintenanceReportRequest(@NotBlank String text) {
}
