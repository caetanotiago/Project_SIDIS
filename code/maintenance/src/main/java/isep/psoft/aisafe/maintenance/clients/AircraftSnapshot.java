package isep.psoft.aisafe.maintenance.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftSnapshot(String registrationNumber, String modelName, LocalDate manufacturingDate,
                               String status) {

    public static final String UNDER_MAINTENANCE = "UNDER_MAINTENANCE";
    public static final String AVAILABLE = "AVAILABLE";
    public static final String INACTIVE = "INACTIVE";

    public boolean isInactive() {
        return INACTIVE.equalsIgnoreCase(status);
    }
}
