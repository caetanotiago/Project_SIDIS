package isep.psoft.aisafe.aircraftmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Representação de uma aeronave. Contrato B1a (Integration-Contracts): registrationNumber,
 * modelName, seatingCapacity e status são obrigatórios e nunca null.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // tolera campos extra (ex.: _links) vindos de outras réplicas
public class AircraftDTO {
    private String registrationNumber;
    private String modelName;
    private Integer seatingCapacity;
    private LocalDate manufacturingDate;
    private String status;
}
