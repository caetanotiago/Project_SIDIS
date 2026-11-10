package isep.psoft.aisafe.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompleteMaintenanceRecordDTO(
        @NotBlank(message = "Completion notes are required") @Size(max = 2000) String notes,
        @NotNull(message = "The cost is required") @PositiveOrZero(message = "The cost cannot be negative") BigDecimal cost,
        @NotNull(message = "The actual duration is required")
        @Positive(message = "The actual duration must be greater than zero minutes") Integer actualDurationMinutes,
        @PastOrPresent(message = "The completion date cannot be in the future") LocalDate completionDate,
        @PositiveOrZero(message = "The aircraft flight hours cannot be negative") Double aircraftFlightHours) {
}
