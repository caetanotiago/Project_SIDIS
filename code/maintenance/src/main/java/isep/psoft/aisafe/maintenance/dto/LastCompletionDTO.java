package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * Resumo por (aeronave, template) usado na US222: última conclusão, horas de voo registadas nessa
 * conclusão e se há um registo desse template em curso. Cada réplica envia o seu; junta-se ficando
 * com a conclusão mais recente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LastCompletionDTO(String aircraftRegistration, String templateId, LocalDate lastCompletionDate,
                                Double aircraftFlightHours, boolean ongoing) {

    public String key() {
        return key(aircraftRegistration, templateId);
    }

    public static String key(String registration, String templateId) {
        return registration.toUpperCase() + "|" + templateId;
    }

    /** Junta dois resumos do mesmo par: conclusão mais recente; "em curso" se algum estiver. */
    public LastCompletionDTO merge(LastCompletionDTO other) {
        LastCompletionDTO latest = this;
        if (other.lastCompletionDate != null
                && (lastCompletionDate == null || other.lastCompletionDate.isAfter(lastCompletionDate))) {
            latest = other;
        }
        return new LastCompletionDTO(aircraftRegistration, templateId, latest.lastCompletionDate,
                latest.aircraftFlightHours, ongoing || other.ongoing);
    }
}
