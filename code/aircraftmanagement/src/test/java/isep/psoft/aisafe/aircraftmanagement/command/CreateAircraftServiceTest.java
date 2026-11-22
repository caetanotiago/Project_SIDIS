package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US102 (command): checks on the read models, write on the owner, one AircraftRegistered event. */
@ExtendWith(MockitoExtension.class)
class CreateAircraftServiceTest {

    @Mock private AircraftRepository repository;
    @Mock private ReplicatedAircraftRepository aircraftReadModel;
    @Mock private ReplicatedAircraftModelRepository modelReadModel;
    @Mock private AircraftManagementEventEmitter events;

    private CreateAircraftService service;

    @BeforeEach
    void setUp() {
        service = new CreateAircraftService(repository, aircraftReadModel, modelReadModel, events);
    }

    private static CreateAircraftDTO dto(String status) {
        CreateAircraftDTO dto = new CreateAircraftDTO();
        dto.setRegistrationNumber("CS-TUA");
        dto.setModelName("A320");
        dto.setSeatingCapacity(180);
        dto.setManufacturingDate(LocalDate.of(2019, 5, 14));
        dto.setStatus(status);
        return dto;
    }

    @Test
    void storesTheAircraftOnThisReplicaAndEmitsAircraftRegistered() {
        when(aircraftReadModel.existsById("CS-TUA")).thenReturn(false);
        when(modelReadModel.existsById("A320")).thenReturn(true);
        when(repository.saveAndFlush(any(Aircraft.class))).thenAnswer(i -> i.getArgument(0));

        Aircraft created = service.create(dto(null));

        assertEquals("CS-TUA", created.getRegistrationNumber());
        assertEquals(AircraftStatus.AVAILABLE, created.getStatus());
        verify(events).aircraftRegistered(created);
    }

    @Test
    void registrationAlreadyKnownInAnyReplicaIsAConflict() {
        when(aircraftReadModel.existsById("CS-TUA")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.create(dto(null)));
        verify(repository, never()).saveAndFlush(any());
        verify(events, never()).aircraftRegistered(any());
    }

    @Test
    void unknownModelIsABadRequest() {
        when(aircraftReadModel.existsById("CS-TUA")).thenReturn(false);
        when(modelReadModel.existsById("A320")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.create(dto(null)));
        verify(events, never()).aircraftRegistered(any());
    }

    @Test
    void invalidStatusIsABadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.create(dto("FLYING_HIGH")));
    }
}
