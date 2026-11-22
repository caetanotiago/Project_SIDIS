package isep.psoft.aisafe.maintenance.events.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduledFlightPayload(String flightId, String aircraftRegistration, Integer estimatedFlightTime,
                                     String status, Long version) {
}
