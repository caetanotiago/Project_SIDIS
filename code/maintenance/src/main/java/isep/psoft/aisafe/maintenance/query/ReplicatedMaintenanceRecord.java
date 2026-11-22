package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "REPLICATED_MAINTENANCE_RECORD")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedMaintenanceRecord {

    @Id
    private String recordId;

    private String aircraftRegistration;
    private String aircraftModel;
    private String templateId;
    private String templateName;
    private String templateType;

    @Column(length = 1000)
    private String description;

    private LocalDate startDate;
    private Integer expectedDurationMinutes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_RECORD_CHECKLIST_ITEM", joinColumns = @JoinColumn(name = "record_id"))
    @OrderColumn(name = "position")
    @Column(name = "item", length = 500)
    private List<String> checklist = new ArrayList<>();

    private String component;
    private String status;
    private LocalDate completionDate;

    @Column(length = 2000)
    private String completionNotes;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    private Integer actualDurationMinutes;
    private Double aircraftFlightHours;

    private Long sourceVersion;
    private String ownerInstance;
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedMaintenanceRecord(String recordId) {
        this.recordId = recordId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(MaintenanceRecordEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        this.aircraftRegistration = p.aircraftRegistration();
        this.aircraftModel = p.aircraftModel();
        this.templateId = p.templateId();
        this.templateName = p.templateName();
        this.templateType = p.templateType();
        this.description = p.description();
        this.startDate = p.startDate();
        this.expectedDurationMinutes = p.expectedDurationMinutes();
        this.checklist = new ArrayList<>(p.checklist() == null ? List.of() : p.checklist());
        this.component = p.component();
        this.status = p.status();
        this.completionDate = p.completionDate();
        this.completionNotes = p.completionNotes();
        this.cost = p.cost();
        this.actualDurationMinutes = p.actualDurationMinutes();
        this.aircraftFlightHours = p.aircraftFlightHours();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }

    public boolean isOngoing() {
        return MaintenanceStatus.IN_PROGRESS.name().equals(status);
    }

    public boolean isCompleted() {
        return MaintenanceStatus.COMPLETED.name().equals(status);
    }

    public long maintenanceMinutes() {
        Integer minutes = isOngoing() ? expectedDurationMinutes : actualDurationMinutes;
        return minutes == null ? 0 : minutes;
    }

    public List<String> getChecklist() {
        return List.copyOf(checklist);
    }
}
