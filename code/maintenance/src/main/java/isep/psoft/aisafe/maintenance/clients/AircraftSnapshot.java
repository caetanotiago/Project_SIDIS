package isep.psoft.aisafe.maintenance.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * Vista local, só de leitura, de uma aeronave do Aircraft Management (contratos B1a/B3a/B3d).
 * Lê apenas os campos de que a Maintenance precisa e ignora os restantes (tolerant reader).
 */
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
