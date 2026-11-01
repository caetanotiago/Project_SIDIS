package isep.psoft.aisafe.aircraftmanagement.dto;

import java.util.Map;

/** US205: resumo da frota por estado + taxa de disponibilidade (AVAILABLE / total, métrica do enunciado). */
public record FleetAvailabilityDTO(Map<String, Long> countByStatus, long total, double availabilityRate) {
}
