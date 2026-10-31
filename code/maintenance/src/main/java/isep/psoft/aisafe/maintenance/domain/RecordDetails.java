package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Value Object: dados indicados ao criar um registo (US115) — descrição, data de início e duração
 * prevista. Um registo representa uma manutenção que já começou (a aeronave fica indisponível),
 * por isso a data de início não pode ser futura.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordDetails {

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    public RecordDetails(String description, LocalDate startDate, Integer expectedDurationMinutes) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("The description is required.");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("The start date is required.");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The start date cannot be in the future.");
        }
        if (expectedDurationMinutes == null || expectedDurationMinutes <= 0) {
            throw new IllegalArgumentException("The expected duration must be greater than zero minutes.");
        }
        this.description = description.trim();
        this.startDate = startDate;
        this.expectedDurationMinutes = expectedDurationMinutes;
    }
}
