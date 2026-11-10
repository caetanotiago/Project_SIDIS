package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;

public record CategorizeRecordDTO(@NotBlank(message = "The component is required") String component) {
}
