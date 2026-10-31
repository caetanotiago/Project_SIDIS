package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * US221: turnaround médio por modelo ("aircraft type", conversa 009). Cada réplica envia somas e
 * contagens; a média só é calculada depois de somar todas as réplicas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnaroundReportDTO(List<TurnaroundItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurnaroundItem(String aircraftModel, long completedRecords, long totalMinutes,
                                 double averageMinutes, double averageHours) {

        public static TurnaroundItem of(String model, long completedRecords, long totalMinutes) {
            double avg = completedRecords == 0 ? 0 : (double) totalMinutes / completedRecords;
            return new TurnaroundItem(model, completedRecords, totalMinutes,
                    Math.round(avg * 100.0) / 100.0, Math.round(avg / 60.0 * 100.0) / 100.0);
        }
    }
}
