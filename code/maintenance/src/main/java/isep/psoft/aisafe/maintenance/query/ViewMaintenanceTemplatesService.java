package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceTemplateAssembler;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ViewMaintenanceTemplatesService {

    private final ReplicatedMaintenanceTemplateRepository readModel;
    private final MaintenanceTemplateAssembler assembler;

    @Transactional(readOnly = true)
    public MaintenanceTemplateDTO getById(String id) {
        return readModel.findById(id)
                .map(assembler::toDTO)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance template not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTemplateDTO> list(String model, String type, String name) {
        TemplateType typeFilter = type == null || type.isBlank() ? null : TemplateType.parse(type);
        return readModel.findAll().stream()
                .filter(t -> model == null || model.isBlank() || t.appliesTo(model))
                .filter(t -> typeFilter == null || typeFilter.name().equals(t.getType()))
                .filter(t -> name == null || name.isBlank() || t.getName().equalsIgnoreCase(name.trim()))
                .sorted(Comparator.comparing(ReplicatedMaintenanceTemplate::getName, String.CASE_INSENSITIVE_ORDER))
                .map(assembler::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReplicatedMaintenanceTemplate> periodic() {
        return readModel.findAll().stream().filter(ReplicatedMaintenanceTemplate::isPeriodic).toList();
    }
}
