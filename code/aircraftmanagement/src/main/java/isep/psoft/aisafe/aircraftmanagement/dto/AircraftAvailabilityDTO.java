package isep.psoft.aisafe.aircraftmanagement.dto;

/** US205: disponibilidade de uma aeronave (available = status AVAILABLE, o único que permite agendar voos). */
public record AircraftAvailabilityDTO(String registrationNumber, String status, boolean available) {
}
