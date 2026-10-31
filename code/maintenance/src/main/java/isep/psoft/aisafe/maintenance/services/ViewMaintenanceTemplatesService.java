package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

/** Consulta de templates (de qualquer réplica). */
@Service
@RequiredArgsConstructor
public class ViewMaintenanceTemplatesService {

    private final MaintenanceReplicaQueries replicas;

    public MaintenanceTemplateDTO getById(String id) {
        return replicas.findTemplate(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance template not found: " + id));
    }

    /** Templates de todas as réplicas; filtros opcionais por modelo, tipo e nome (exato, sem maiúsculas). */
    public List<MaintenanceTemplateDTO> list(String model, String type, String name) {
        TemplateType typeFilter = type == null || type.isBlank() ? null : TemplateType.parse(type);
        Predicate<MaintenanceTemplate> local = t ->
                (model == null || model.isBlank() || t.appliesTo(model))
                        && (typeFilter == null || t.getType() == typeFilter)
                        && (name == null || name.isBlank() || t.getName().equalsIgnoreCase(name.trim()));
        String peerUri = MaintenanceReplicaQueries.uri("/api/maintenance-templates",
                "model", model, "type", typeFilter, "name", name);
        return replicas.listTemplates(local, peerUri);
    }
}
