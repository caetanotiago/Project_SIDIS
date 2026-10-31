package isep.psoft.aisafe.aircraftmanagement.dto;

/** US206: horas operacionais de uma aeronave (0 se ainda não tem voos agendados). */
public record AircraftOperationalHoursDTO(String registrationNumber, String modelName,
                                          long totalMinutes, double totalHours) {

    public static AircraftOperationalHoursDTO of(String registrationNumber, String modelName, long totalMinutes) {
        double hours = Math.round(totalMinutes / 60.0 * 100.0) / 100.0;
        return new AircraftOperationalHoursDTO(registrationNumber, modelName, totalMinutes, hours);
    }
}
