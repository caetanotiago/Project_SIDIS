package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.factories.MaintenanceRecordFactory;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US115 (registos). Ordem: aeronave (B3a) → template (qualquer réplica) → aplicabilidade → registo
 * gravado e flushed → aeronave UNDER_MAINTENANCE (B3b) → commit. Se o B3b falhar nada fica gravado.
 */
@Service
@RequiredArgsConstructor
public class CreateMaintenanceRecordService {

    private final AircraftClient aircraftClient;
    private final MaintenanceReplicaQueries replicas;
    private final MaintenanceRecordFactory factory;
    private final MaintenanceRecordRepository repository;
    private final AircraftAvailabilityService availability;

    @Transactional
    public MaintenanceRecord create(CreateMaintenanceRecordDTO dto) {
        String registration = dto.aircraftRegistration().trim().toUpperCase();
        AircraftSnapshot aircraft = aircraftClient.getAircraft(registration);

        MaintenanceTemplateDTO template = replicas.findTemplate(dto.templateId().trim())
                .orElseThrow(() -> new EntityNotFoundException("Maintenance template not found: " + dto.templateId()));
        if (!template.appliesTo(aircraft.modelName())) {
            throw new IllegalArgumentException("Maintenance template '" + template.getName()
                    + "' does not apply to aircraft model " + aircraft.modelName() + ".");
        }

        MaintenanceRecord saved = repository.saveAndFlush(factory.create(aircraft, template, dto));
        availability.markUnderMaintenance(aircraft);
        return saved;
    }
}
