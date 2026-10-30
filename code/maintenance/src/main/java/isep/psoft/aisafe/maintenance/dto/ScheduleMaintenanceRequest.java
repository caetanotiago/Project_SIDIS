package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for scheduling an aircraft maintenance intervention.
 *
 * @author Diogo Fernandes - 1240632
 */
public record ScheduleMaintenanceRequest(
        @NotBlank String aircraftRegistration,
        @NotNull @Future LocalDate scheduledDate,
        @NotBlank String serviceType) {
}
