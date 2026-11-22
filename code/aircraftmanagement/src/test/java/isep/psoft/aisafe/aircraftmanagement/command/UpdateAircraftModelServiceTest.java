package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.sidis.common.peers.PeerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US201 (command): owner-only write and the new AircraftModelUpdated event. */
@ExtendWith(MockitoExtension.class)
class UpdateAircraftModelServiceTest {

    @Mock private AircraftModelRepository repository;
    @Mock private PeerClient peerClient;
    @Mock private AircraftManagementEventEmitter events;

    private UpdateAircraftModelService service;

    @BeforeEach
    void setUp() {
        service = new UpdateAircraftModelService(repository, peerClient, new AircraftAssembler(), events);
    }

    @Test
    void ownerUpdatesTheRangeAndEmitsAircraftModelUpdated() {
        AircraftModel model = new AircraftModel("A320", "Airbus", 180, 24210, 6100, 828);
        when(repository.findById("A320")).thenReturn(Optional.of(model));
        when(repository.saveAndFlush(model)).thenReturn(model);

        AircraftModelDTO result = service.update("A320", new UpdateAircraftModelDTO(null, null, null, 6300.0, null));

        assertEquals(6300.0, result.getMaximumRange());
        assertEquals("Airbus", result.getManufacturer());
        verify(events).aircraftModelUpdated(model);
        verifyNoInteractions(peerClient);
    }

    @Test
    void modelOfAnotherReplicaIsForwardedToItsOwner() {
        UpdateAircraftModelDTO body = new UpdateAircraftModelDTO(null, null, null, 12000.0, null);
        AircraftModelDTO fromOwner = new AircraftModelDTO();
        fromOwner.setModelName("A330");
        when(repository.findById("A330")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircraft-models/A330"), eq(body),
                eq(AircraftModelDTO.class))).thenReturn(Optional.of(fromOwner));

        assertSame(fromOwner, service.update("A330", body));
        verify(events, never()).aircraftModelUpdated(any());
    }

    @Test
    void emptyBodyIsABadRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update("A320", new UpdateAircraftModelDTO(null, null, null, null, null)));
        verifyNoInteractions(repository, peerClient, events);
    }
}
