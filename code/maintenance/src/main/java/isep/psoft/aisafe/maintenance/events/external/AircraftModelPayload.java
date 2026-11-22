package isep.psoft.aisafe.maintenance.events.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftModelPayload(String modelName, String manufacturer, Long version) {
}
