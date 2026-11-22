package isep.psoft.aisafe.maintenance.events.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftPayload(String registrationNumber, String modelName, LocalDate manufacturingDate,
                              String status, Long version) {
}
