package isep.psoft.aisafe.aircraftmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** US102. A matrícula segue o formato PREFIXO-SUFIXO (ex.: CS-TUA); decisão Q10 por confirmar. */
@Data
@NoArgsConstructor
public class CreateAircraftDTO {

    @NotBlank(message = "Registration number is required")
    @Pattern(regexp = "^[A-Z0-9]{1,2}-[A-Z0-9]{2,5}$",
            message = "Registration number must look like CS-TUA (upper case)")
    private String registrationNumber;

    @NotBlank(message = "Model name is required")
    private String modelName;

    @NotNull(message = "Seating capacity is required")
    @Positive(message = "Seating capacity must be positive")
    private Integer seatingCapacity;

    @NotNull(message = "Manufacturing date is required")
    @PastOrPresent(message = "Manufacturing date cannot be in the future")
    private LocalDate manufacturingDate;

    /** Opcional; por omissão AVAILABLE. */
    private String status;
}
