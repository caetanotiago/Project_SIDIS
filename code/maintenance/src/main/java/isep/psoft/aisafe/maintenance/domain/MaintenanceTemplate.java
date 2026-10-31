package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root: template de manutenção (US115). Define o tipo de manutenção, os modelos de
 * aeronave a que se aplica (referenciados pelo nome — pertencem ao Aircraft Management), a checklist
 * que é copiada para cada registo e, opcionalmente, a periodicidade usada nos alertas (US222).
 */
@Entity
@Table(name = "maintenance_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceTemplate {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateType type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_applicable_model", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "model_name", nullable = false)
    private Set<String> applicableModels = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "template_checklist_item", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "item", nullable = false, length = 500)
    private List<String> checklist = new ArrayList<>();

    @Embedded
    private MaintenanceInterval interval;

    @Version
    private Long version;

    private MaintenanceTemplate(String id, String name, TemplateType type, Collection<String> applicableModels,
                                List<String> checklist, MaintenanceInterval interval) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The template name is required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("The template type is required.");
        }
        this.id = id;
        this.name = name.trim();
        this.type = type;
        this.applicableModels = normalizeModels(applicableModels);
        this.checklist = normalizeChecklist(checklist);
        this.interval = interval;
    }

    /** Template criado por um utilizador: identificador aleatório (UUID). */
    public static MaintenanceTemplate create(String name, TemplateType type, Collection<String> applicableModels,
                                             List<String> checklist, MaintenanceInterval interval) {
        return new MaintenanceTemplate(UUID.randomUUID().toString(), name, type, applicableModels, checklist, interval);
    }

    /**
     * Template de referência (WP#0A), carregado em TODAS as réplicas com um id derivado do nome:
     * as três cópias são o mesmo template e a junção por id elimina os duplicados.
     */
    public static MaintenanceTemplate reference(String name, TemplateType type, Collection<String> applicableModels,
                                                List<String> checklist, MaintenanceInterval interval) {
        return new MaintenanceTemplate(referenceId(name), name, type, applicableModels, checklist, interval);
    }

    public static String referenceId(String name) {
        String key = "aisafe-maintenance-template:" + name.trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** O template aplica-se a este modelo de aeronave? (comparação sem distinguir maiúsculas) */
    public boolean appliesTo(String modelName) {
        return modelName != null && applicableModels.stream().anyMatch(m -> m.equalsIgnoreCase(modelName.trim()));
    }

    public boolean isPeriodic() {
        return interval != null;
    }

    public List<String> getChecklist() {
        return List.copyOf(checklist);
    }

    public Set<String> getApplicableModels() {
        return Set.copyOf(applicableModels);
    }

    private static Set<String> normalizeModels(Collection<String> models) {
        if (models == null || models.isEmpty()) {
            throw new IllegalArgumentException("A template must apply to at least one aircraft model.");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String model : models) {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("Aircraft model names cannot be blank.");
            }
            result.add(model.trim());
        }
        return result;
    }

    private static List<String> normalizeChecklist(List<String> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("The checklist must have at least one item.");
        }
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Checklist items cannot be blank.");
            }
            result.add(item.trim());
        }
        return result;
    }
}
