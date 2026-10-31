package isep.psoft.aisafe.aircraftmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contrato B1b: modelName e maximumRange (km, nunca null). */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AircraftModelDTO {
    private String modelName;
    private String manufacturer;
    private Integer seatingCapacity;
    private Double fuelCapacity;
    private Double maximumRange;
    private Double cruisingSpeed;
}
