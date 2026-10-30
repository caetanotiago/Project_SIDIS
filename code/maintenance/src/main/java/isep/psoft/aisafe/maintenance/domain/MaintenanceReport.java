package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable completion details for a maintenance intervention.
 *
 * @author Diogo Fernandes - 1240632
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceReport {

    @Column(name = "completion_report", length = 4000)
    private String text;

    public MaintenanceReport(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Completion report is required.");
        }
        this.text = text.trim();
    }
}
