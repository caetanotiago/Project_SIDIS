package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.factories.MaintenanceRecordFactory;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMaintenanceRecordServiceTest {

    @Mock AircraftClient aircraftClient;
    @Mock MaintenanceReplicaQueries replicas;
    @Mock MaintenanceRecordRepository repository;
    @Mock AircraftAvailabilityService availability;

    private CreateMaintenanceRecordService service;

    private static final AircraftSnapshot A320 = new AircraftSnapshot("CS-TUA", "A320", LocalDate.of(2019, 5, 14), "AVAILABLE");

    @BeforeEach
    void setUp() {
        service = new CreateMaintenanceRecordService(aircraftClient, replicas, new MaintenanceRecordFactory(),
                repository, availability);
    }

    static MaintenanceTemplateDTO template(String... models) {
        MaintenanceTemplateDTO t = new MaintenanceTemplateDTO();
        t.setId("tpl-1");
        t.setName("A-Check");
        t.setType("INSPECTION");
        t.setApplicableModels(List.of(models));
        t.setChecklist(List.of("Inspect tyres", "Check oil"));
        return t;
    }

    static CreateMaintenanceRecordDTO request(String component) {
        return new CreateMaintenanceRecordDTO(" cs-tua ", "tpl-1", "Routine A-check", LocalDate.now(), 480, component);
    }

    @Test
    void createsTheRecordThenPutsTheAircraftUnderMaintenance() {
        when(aircraftClient.getAircraft("CS-TUA")).thenReturn(A320);
        when(replicas.findTemplate("tpl-1")).thenReturn(Optional.of(template("A320")));
        when(repository.saveAndFlush(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        MaintenanceRecord created = service.create(request("engine"));

        assertEquals("CS-TUA", created.getAircraftRegistration());
        assertEquals("A320", created.getAircraftModel());
        assertEquals(List.of("Inspect tyres", "Check oil"), created.getChecklist());
        assertEquals(MaintenanceComponent.ENGINE, created.getComponent());
        InOrder order = inOrder(aircraftClient, repository, availability);
        order.verify(aircraftClient).getAircraft("CS-TUA");
        order.verify(repository).saveAndFlush(any(MaintenanceRecord.class));
        order.verify(availability).markUnderMaintenance(A320);
    }

    @Test
    void unknownAircraftCreatesNothing() {
        when(aircraftClient.getAircraft("CS-TUA")).thenThrow(new EntityNotFoundException("Aircraft not found: CS-TUA"));

        assertThrows(EntityNotFoundException.class, () -> service.create(request(null)));
        verifyNoInteractions(repository, availability);
    }

    @Test
    void unknownTemplateOrTemplateNotApplicableToTheModelIsRejected() {
        when(aircraftClient.getAircraft("CS-TUA")).thenReturn(A320);
        when(replicas.findTemplate("tpl-1")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.create(request(null)));

        when(replicas.findTemplate("tpl-1")).thenReturn(Optional.of(template("E190")));
        assertThrows(IllegalArgumentException.class, () -> service.create(request(null)));
        verifyNoInteractions(repository, availability);
    }

    @Test
    void invalidComponentIsRejectedBeforeSaving() {
        when(aircraftClient.getAircraft("CS-TUA")).thenReturn(A320);
        when(replicas.findTemplate("tpl-1")).thenReturn(Optional.of(template("A320")));

        assertThrows(IllegalArgumentException.class, () -> service.create(request("wings")));
        verifyNoInteractions(repository, availability);
    }

    @Test
    void whenAircraftManagementIsDownTheExceptionPropagatesSoTheTransactionRollsBack() {
        when(aircraftClient.getAircraft("CS-TUA")).thenReturn(A320);
        when(replicas.findTemplate("tpl-1")).thenReturn(Optional.of(template("A320")));
        when(repository.saveAndFlush(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RemoteServiceUnavailableException("Aircraft Management", null))
                .when(availability).markUnderMaintenance(A320);

        assertThrows(RemoteServiceUnavailableException.class, () -> service.create(request(null)));
    }
}
