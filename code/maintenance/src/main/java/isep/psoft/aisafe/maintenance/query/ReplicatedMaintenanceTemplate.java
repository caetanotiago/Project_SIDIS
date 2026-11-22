package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceTemplateEventPayload;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "REPLICATED_MAINTENANCE_TEMPLATE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedMaintenanceTemplate {

    @Id
    private String templateId;

    private String name;
    private String type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_TEMPLATE_MODEL", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "model_name")
    private List<String> applicableModels = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "REPLICATED_TEMPLATE_CHECKLIST_ITEM", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "item", length = 500)
    private List<String> checklist = new ArrayList<>();

    private Integer flightHoursInterval;
    private Integer calendarDaysInterval;

    private Long sourceVersion;
    private String ownerInstance;
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedMaintenanceTemplate(String templateId) {
        this.templateId = templateId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(MaintenanceTemplateEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        this.name = p.name();
        this.type = p.type();
        this.applicableModels = new ArrayList<>(p.applicableModels() == null ? List.of() : p.applicableModels());
        this.checklist = new ArrayList<>(p.checklist() == null ? List.of() : p.checklist());
        this.flightHoursInterval = p.flightHoursInterval();
        this.calendarDaysInterval = p.calendarDaysInterval();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }

    public boolean appliesTo(String modelName) {
        return modelName != null && applicableModels.stream().anyMatch(m -> m.equalsIgnoreCase(modelName.trim()));
    }

    public boolean isPeriodic() {
        return flightHoursInterval != null || calendarDaysInterval != null;
    }

    public List<String> getApplicableModels() {
        return List.copyOf(applicableModels);
    }

    public List<String> getChecklist() {
        return List.copyOf(checklist);
    }
}
