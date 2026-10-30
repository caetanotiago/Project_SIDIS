package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Aggregate root representing a maintenance intervention for an aircraft.
 *
 * @author Diogo Fernandes - 1240632
 */
@Entity
@Table(name = "maintenance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "aircraft_registration", nullable = false)
    private String aircraftRegistration;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Embedded
    private MaintenanceReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceStatus status;

    @Version
    private Long version;

    public Maintenance(String aircraftRegistration, LocalDate scheduledDate, String serviceType) {
        if (aircraftRegistration == null || aircraftRegistration.isBlank()) {
            throw new IllegalArgumentException("Aircraft registration is required.");
        }
        if (scheduledDate == null || !scheduledDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Scheduled date must be in the future.");
        }
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("Service type is required.");
        }

        this.aircraftRegistration = aircraftRegistration.trim();
        this.scheduledDate = scheduledDate;
        this.serviceType = serviceType.trim();
        this.status = MaintenanceStatus.SCHEDULED;
    }

    public void complete(MaintenanceReport report, LocalDate completionDate) {
        validateCompletion(report, completionDate);

        this.report = report;
        this.completionDate = completionDate;
        this.status = MaintenanceStatus.COMPLETED;
    }

    public void validateCompletion(MaintenanceReport report, LocalDate completionDate) {
        if (status != MaintenanceStatus.SCHEDULED && status != MaintenanceStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only scheduled or in-progress maintenance can be completed.");
        }
        if (report == null) {
            throw new IllegalArgumentException("Completion report is required.");
        }
        if (completionDate == null || completionDate.isBefore(scheduledDate)) {
            throw new IllegalArgumentException("Completion date cannot be before the scheduled date.");
        }
    }
}
