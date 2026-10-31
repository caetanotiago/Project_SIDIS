package isep.psoft.aisafe.maintenance.factories;

import isep.psoft.aisafe.maintenance.domain.MaintenanceInterval;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceTemplateDTO;
import org.springframework.stereotype.Component;

/** Factory (Creator): converte o pedido da US115 (templates) num aggregate válido. */
@Component
public class MaintenanceTemplateFactory {

    public MaintenanceTemplate create(CreateMaintenanceTemplateDTO dto) {
        return MaintenanceTemplate.create(
                dto.name(),
                TemplateType.parse(dto.type()),
                dto.applicableModels(),
                dto.checklist(),
                MaintenanceInterval.ofOptional(dto.flightHoursInterval(), dto.calendarDaysInterval()));
    }
}
