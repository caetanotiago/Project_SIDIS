package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * US115 (registos): matrícula, template ("maintenance type"), descrição, data de início, duração
 * prevista (minutos) e, opcionalmente, o componente (US217). A checklist vem do template.
 */
public record CreateMaintenanceRecordDTO(
        @NotBlank(message = "The aircraft registration is required") String aircraftRegistration,
        @NotBlank(message = "The template id is required") String templateId,
        @NotBlank(message = "The description is required") @Size(max = 1000) String description,
        @NotNull(message = "The start date is required")
        @PastOrPresent(message = "The start date cannot be in the future") LocalDate startDate,
        @NotNull(message = "The expected duration is required")
        @Positive(message = "The expected duration must be greater than zero minutes") Integer expectedDurationMinutes,
        String component) {
}
