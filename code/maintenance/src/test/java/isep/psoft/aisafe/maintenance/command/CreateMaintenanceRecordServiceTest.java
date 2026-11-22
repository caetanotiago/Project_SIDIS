package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.dto.CreateMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.factories.MaintenanceRecordFactory;
import isep.psoft.aisafe.maintenance.query.ReadModelFixtures;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceTemplateRepository;
import isep.psoft.aisafe.maintenance.query.external.FleetDirectory;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateMaintenanceRecordServiceTest {

    @Mock FleetDirectory fleet;
    @Mock ReplicatedMaintenanceTemplateRepository templates;
    @Mock MaintenanceRecordRepository repository;
    @Mock AircraftAvailabilityService availability;
    @Mock MaintenanceEventEmitter events;

    private CreateMaintenanceRecordService service;

    private static final AircraftSnapshot A320 = new AircraftSnapshot("CS-TUA", "A320", LocalDate.of(2019, 5, 14), "AVAILABLE");

    @BeforeEach
    void setUp() {
        service = new CreateMaintenanceRecordService(fleet, templates, new MaintenanceRecordFactory(), repository,
                availability, events);
    }

    static CreateMaintenanceRecordDTO request(String component) {
        return new CreateMaintenanceRecordDTO(" cs-tua ", "tpl-1", "Routine A-check", LocalDate.now(), 480, component);
    }

    @Test
    void savesTheRecordPutsTheAircraftUnderMaintenanceAndThenEmitsTheEvent() {
        when(fleet.aircraft("CS-TUA")).thenReturn(A320);
        when(templates.findById("tpl-1")).thenReturn(Optional.of(ReadModelFixtures.template("tpl-1", "A-Check", 500, 120, "A320")));
        when(repository.saveAndFlush(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        MaintenanceRecord created = service.create(request("engine"));

        assertEquals("CS-TUA", created.getAircraftRegistration());
        assertEquals("A320", created.getAircraftModel());
        assertEquals(List.of("Inspect tyres", "Check oil"), created.getChecklist());
        assertEquals(MaintenanceComponent.ENGINE, created.getComponent());
        InOrder order = inOrder(fleet, repository, availability, events);
        order.verify(fleet).aircraft("CS-TUA");
        order.verify(repository).saveAndFlush(any(MaintenanceRecord.class));
        order.verify(availability).markUnderMaintenance(A320);
        order.verify(events).recordCreated(created);
    }

    @Test
    void unknownAircraftCreatesNothingAndEmitsNothing() {
        when(fleet.aircraft("CS-TUA")).thenThrow(new EntityNotFoundException("Aircraft not found: CS-TUA"));

        assertThrows(EntityNotFoundException.class, () -> service.create(request(null)));
        verifyNoInteractions(repository, availability, events);
    }

    @Test
    void unknownTemplateOrTemplateNotApplicableToTheModelIsRejected() {
        when(fleet.aircraft("CS-TUA")).thenReturn(A320);
        when(templates.findById("tpl-1")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.create(request(null)));

        when(templates.findById("tpl-1")).thenReturn(Optional.of(ReadModelFixtures.template("tpl-1", "E-Check", 500, 120, "E190")));
        assertThrows(IllegalArgumentException.class, () -> service.create(request(null)));
        verifyNoInteractions(repository, availability, events);
    }

    @Test
    void whenAircraftManagementRefusesNoEventIsEmittedAndTheExceptionRollsBack() {
        when(fleet.aircraft("CS-TUA")).thenReturn(A320);
        when(templates.findById("tpl-1")).thenReturn(Optional.of(ReadModelFixtures.template("tpl-1", "A-Check", 500, 120, "A320")));
        when(repository.saveAndFlush(any(MaintenanceRecord.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RemoteServiceUnavailableException("Aircraft Management", null))
                .when(availability).markUnderMaintenance(A320);

        assertThrows(RemoteServiceUnavailableException.class, () -> service.create(request(null)));
        verifyNoInteractions(events);
    }
}
