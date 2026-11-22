package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.factories.MaintenanceTemplateFactory;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceTemplateRepository;
import isep.psoft.aisafe.maintenance.query.external.FleetDirectory;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateMaintenanceTemplateService {

    private final MaintenanceTemplateRepository repository;
    private final ReplicatedMaintenanceTemplateRepository readModel;
    private final MaintenanceTemplateFactory factory;
    private final FleetDirectory fleet;
    private final MaintenanceEventEmitter events;

    @Transactional
    public MaintenanceTemplate create(CreateMaintenanceTemplateDTO dto) {
        MaintenanceTemplate template = factory.create(dto);

        if (repository.existsByNameIgnoreCase(template.getName()) || readModel.existsByNameIgnoreCase(template.getName())) {
            throw new IllegalArgumentException("A maintenance template named '" + template.getName() + "' already exists.");
        }
        for (String model : template.getApplicableModels()) {
            if (!fleet.modelExists(model)) {
                throw new IllegalArgumentException("Unknown aircraft model: " + model);
            }
        }
        MaintenanceTemplate saved = repository.saveAndFlush(template);
        events.templateCreated(saved);
        return saved;
    }
}
