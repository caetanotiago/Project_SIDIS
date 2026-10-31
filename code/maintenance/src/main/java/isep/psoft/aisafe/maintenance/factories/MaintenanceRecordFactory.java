package isep.psoft.aisafe.maintenance.factories;

import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.RecordDetails;
import isep.psoft.aisafe.maintenance.domain.TemplateReference;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import org.springframework.stereotype.Component;

/**
 * Factory (Creator) da US115: cria o registo a partir da aeronave (B3a) e do template (de qualquer
 * réplica), copiando a checklist do template e guardando a referência ao template e o modelo da aeronave.
 */
@Component
public class MaintenanceRecordFactory {

    public MaintenanceRecord create(AircraftSnapshot aircraft, MaintenanceTemplateDTO template,
                                    CreateMaintenanceRecordDTO dto) {
        TemplateReference reference = new TemplateReference(
                template.getId(), template.getName(), TemplateType.parse(template.getType()));
        RecordDetails details = new RecordDetails(dto.description(), dto.startDate(), dto.expectedDurationMinutes());
        return new MaintenanceRecord(
                aircraft.registrationNumber(),
                aircraft.modelName(),
                reference,
                template.getChecklist(),
                details,
                MaintenanceComponent.parseOptional(dto.component()));
    }
}
