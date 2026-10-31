package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * US115 (templates): nome, tipo (INSPECTION, SCHEDULED_MAINTENANCE, OVERHAUL, MODIFICATION), modelos
 * de aeronave a que se aplica, checklist e, opcionalmente, a periodicidade (US222).
 */
public record CreateMaintenanceTemplateDTO(
        @NotBlank(message = "The template name is required") @Size(max = 200) String name,
        @NotBlank(message = "The template type is required") String type,
        @NotEmpty(message = "At least one applicable aircraft model is required") List<String> applicableModels,
        @NotEmpty(message = "The checklist must have at least one item") List<String> checklist,
        @Positive(message = "The flight hours interval must be positive") Integer flightHoursInterval,
        @Positive(message = "The calendar days interval must be positive") Integer calendarDaysInterval) {
}
