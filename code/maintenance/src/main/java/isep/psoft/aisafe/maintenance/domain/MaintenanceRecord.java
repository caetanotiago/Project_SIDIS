package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root: registo de manutenção de uma aeronave (US115, US119, US217).
 *
 * <p>Referencia a aeronave e o template por identidade (estão noutro serviço / podem estar noutra
 * réplica) e guarda uma cópia do modelo da aeronave, do template e da checklist no momento da
 * criação. Ciclo de vida: IN_PROGRESS → COMPLETED (uma única vez).
 */
@Entity
@Table(name = "maintenance_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(name = "aircraft_registration", nullable = false)
    private String aircraftRegistration;

    @Column(name = "aircraft_model", nullable = false)
    private String aircraftModel;

    @Embedded
    private TemplateReference template;

    @Embedded
    private RecordDetails details;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "record_checklist_item", joinColumns = @JoinColumn(name = "record_id"))
    @OrderColumn(name = "position")
    @Column(name = "item", nullable = false, length = 500)
    private List<String> checklist = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private MaintenanceComponent component;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceStatus status;

    @Embedded
    private CompletionDetails completion;

    public MaintenanceRecord(String aircraftRegistration, String aircraftModel, TemplateReference template,
                             List<String> checklist, RecordDetails details, MaintenanceComponent component) {
        if (aircraftRegistration == null || aircraftRegistration.isBlank()) {
            throw new IllegalArgumentException("The aircraft registration is required.");
        }
        if (aircraftModel == null || aircraftModel.isBlank()) {
            throw new IllegalArgumentException("The aircraft model is required.");
        }
        if (template == null || details == null) {
            throw new IllegalArgumentException("The template and the record details are required.");
        }
        if (checklist == null || checklist.isEmpty()) {
            throw new IllegalArgumentException("The checklist defined by the template is required.");
        }
        this.aircraftRegistration = aircraftRegistration.trim();
        this.aircraftModel = aircraftModel.trim();
        this.template = template;
        this.checklist = new ArrayList<>(checklist);
        this.details = details;
        this.component = component;
        this.status = MaintenanceStatus.IN_PROGRESS;
    }

    /** US119: conclui o registo. Só uma vez; a data de conclusão não pode ser anterior ao início. */
    public void complete(CompletionDetails completionDetails) {
        if (completionDetails == null) {
            throw new IllegalArgumentException("The completion details are required.");
        }
        if (!isOngoing()) {
            throw new IllegalStateException("Maintenance record " + id + " is already completed.");
        }
        if (completionDetails.getCompletionDate().isBefore(details.getStartDate())) {
            throw new IllegalArgumentException("The completion date cannot be before the start date ("
                    + details.getStartDate() + ").");
        }
        this.completion = completionDetails;
        this.status = MaintenanceStatus.COMPLETED;
    }

    /** US217: categoriza o registo por componente (só enquanto está em curso). */
    public void categorize(MaintenanceComponent newComponent) {
        if (newComponent == null) {
            throw new IllegalArgumentException("The maintenance component is required.");
        }
        if (!isOngoing()) {
            throw new IllegalStateException("Maintenance record " + id + " is completed and cannot be re-categorised.");
        }
        this.component = newComponent;
    }

    public boolean isOngoing() {
        return status == MaintenanceStatus.IN_PROGRESS;
    }

    /** US117: duração real se concluído, senão a duração prevista. */
    public long maintenanceMinutes() {
        return isOngoing() ? details.getExpectedDurationMinutes() : completion.getActualDurationMinutes();
    }

    public List<String> getChecklist() {
        return List.copyOf(checklist);
    }
}
