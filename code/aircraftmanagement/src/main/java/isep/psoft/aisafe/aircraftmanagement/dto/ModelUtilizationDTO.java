package isep.psoft.aisafe.aircraftmanagement.dto;

/** US204: utilização de um modelo = soma dos minutos de voo de todas as suas aeronaves. */
public record ModelUtilizationDTO(String modelName, long totalMinutes, double totalHours, int aircraftCount) {

    public static ModelUtilizationDTO of(String modelName, long totalMinutes, int aircraftCount) {
        double hours = Math.round(totalMinutes / 60.0 * 100.0) / 100.0;
        return new ModelUtilizationDTO(modelName, totalMinutes, hours, aircraftCount);
    }
}
