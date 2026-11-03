package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LastCompletionDTO(String aircraftRegistration, String templateId, LocalDate lastCompletionDate,
                                Double aircraftFlightHours, boolean ongoing) {

    public String key() {
        return key(aircraftRegistration, templateId);
    }

    public static String key(String registration, String templateId) {
        return registration.toUpperCase() + "|" + templateId;
    }

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
