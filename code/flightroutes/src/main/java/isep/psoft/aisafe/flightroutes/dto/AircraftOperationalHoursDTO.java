package isep.psoft.aisafe.flightroutes.dto;

// US206 - Horas operacionais por aeronave, calculadas a partir dos voos agendados de TODAS as
// réplicas (soma dos minutos de cada voo). Pedido pelo microsserviço Aircraft Management.
public class AircraftOperationalHoursDTO {

    private String registrationNumber;

    private Long totalMinutes;

    public AircraftOperationalHoursDTO() {}

    public AircraftOperationalHoursDTO(String registrationNumber, Long totalMinutes) {
        this.registrationNumber = registrationNumber;
        this.totalMinutes = totalMinutes;
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public Long getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }

    // Campo exposto no JSON da resposta: horas com 2 casas decimais.
    // Ex: 90 minutos -> 1.5
    public Double getTotalHours() {
        if (totalMinutes == null) return 0.0;
        return Math.round((totalMinutes / 60.0) * 100) / 100.0;
    }
}
