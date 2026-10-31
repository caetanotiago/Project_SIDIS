package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Value Object: dados de conclusão de um registo (US119) — notas, custo (US220), duração real
 * (turnaround da US221 e horas da US117) e, opcionalmente, as horas de voo da aeronave nesse
 * momento (base do critério de horas da US222).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompletionDetails {

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "completion_notes", length = 2000)
    private String notes;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Column(name = "aircraft_flight_hours")
    private Double aircraftFlightHours;

    public CompletionDetails(LocalDate completionDate, String notes, BigDecimal cost,
                             Integer actualDurationMinutes, Double aircraftFlightHours) {
        if (completionDate == null) {
            throw new IllegalArgumentException("The completion date is required.");
        }
        if (completionDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The completion date cannot be in the future.");
        }
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Completion notes are required.");
        }
        if (cost == null || cost.signum() < 0) {
            throw new IllegalArgumentException("The maintenance cost is required and cannot be negative.");
        }
        if (actualDurationMinutes == null || actualDurationMinutes <= 0) {
            throw new IllegalArgumentException("The actual duration must be greater than zero minutes.");
        }
        if (aircraftFlightHours != null && aircraftFlightHours < 0) {
            throw new IllegalArgumentException("The aircraft flight hours cannot be negative.");
        }
        this.completionDate = completionDate;
        this.notes = notes.trim();
        this.cost = cost;
        this.actualDurationMinutes = actualDurationMinutes;
        this.aircraftFlightHours = aircraftFlightHours;
    }
}
