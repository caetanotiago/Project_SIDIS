package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.factories.MaintenanceTemplateFactory;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateMaintenanceTemplateService {

    private final MaintenanceTemplateRepository repository;
    private final MaintenanceTemplateFactory factory;
    private final ViewMaintenanceTemplatesService viewTemplates;
    private final AircraftClient aircraftClient;

    @Transactional
    public MaintenanceTemplate create(CreateMaintenanceTemplateDTO dto) {
        MaintenanceTemplate template = factory.create(dto);

        if (repository.existsByNameIgnoreCase(template.getName())
                || !viewTemplates.list(null, null, template.getName()).isEmpty()) {
            throw new IllegalArgumentException("A maintenance template named '" + template.getName() + "' already exists.");
        }
        for (String model : template.getApplicableModels()) {
            if (!aircraftClient.modelExists(model)) {
                throw new IllegalArgumentException("Unknown aircraft model: " + model);
            }
        }
        return repository.saveAndFlush(template);
    }
}
