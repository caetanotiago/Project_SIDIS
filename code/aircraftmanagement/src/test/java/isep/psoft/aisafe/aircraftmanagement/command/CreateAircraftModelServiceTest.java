package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US101 (command): uniqueness on the read model, write on the owner, one AircraftModelCreated event. */
@ExtendWith(MockitoExtension.class)
class CreateAircraftModelServiceTest {

    @Mock private AircraftModelRepository repository;
    @Mock private ReplicatedAircraftModelRepository readModel;
    @Mock private AircraftManagementEventEmitter events;

    private CreateAircraftModelService service;

    @BeforeEach
    void setUp() {
        service = new CreateAircraftModelService(repository, readModel, events);
    }

    private static CreateAircraftModelDTO dto() {
        CreateAircraftModelDTO dto = new CreateAircraftModelDTO();
        dto.setModelName("A320");
        dto.setManufacturer("Airbus");
        dto.setSeatingCapacity(180);
        dto.setFuelCapacity(24210.0);
        dto.setMaximumRange(6100.0);
        dto.setCruisingSpeed(828.0);
        return dto;
    }

    @Test
    void storesTheModelAndEmitsAircraftModelCreated() {
        when(readModel.existsById("A320")).thenReturn(false);
        when(repository.saveAndFlush(any(AircraftModel.class))).thenAnswer(i -> i.getArgument(0));

        AircraftModel created = service.create(dto());

        assertEquals(6100.0, created.getMaximumRange());
        verify(events).aircraftModelCreated(created);
    }

    @Test
    void modelNameAlreadyKnownInAnyReplicaIsAConflict() {
        when(readModel.existsById("A320")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.create(dto()));
        verify(repository, never()).saveAndFlush(any());
        verify(events, never()).aircraftModelCreated(any());
    }
}
