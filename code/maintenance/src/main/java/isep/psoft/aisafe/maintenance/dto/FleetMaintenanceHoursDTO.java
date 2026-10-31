package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * US117: horas de manutenção da frota (duração real dos registos concluídos + prevista dos em curso).
 * Os minutos são somáveis entre réplicas; as horas calculam-se depois da soma.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FleetMaintenanceHoursDTO(long totalMinutes, double totalHours, List<AircraftHours> aircraft) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AircraftHours(String aircraftRegistration, String aircraftModel, long records,
                                long minutes, double hours) {
    }

    public static double hours(long minutes) {
        return Math.round(minutes / 60.0 * 100.0) / 100.0;
    }
}
