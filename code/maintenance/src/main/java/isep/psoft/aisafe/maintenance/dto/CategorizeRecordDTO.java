package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;

/** US217: ENGINE, AIRFRAME, AVIONICS, INTERIOR ou EXTERIOR. */
public record CategorizeRecordDTO(@NotBlank(message = "The component is required") String component) {
}
