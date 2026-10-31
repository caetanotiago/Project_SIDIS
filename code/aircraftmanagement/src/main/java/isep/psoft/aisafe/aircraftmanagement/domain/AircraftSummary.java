package isep.psoft.aisafe.aircraftmanagement.domain;

import java.time.LocalDate;

/** Read model imutável de uma aeronave, independente da réplica onde está guardada. */
public record AircraftSummary(String registrationNumber, String modelName, int seatingCapacity,
                              LocalDate manufacturingDate, AircraftStatus status) {
}
