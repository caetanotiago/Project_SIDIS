package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.time.LocalDate;
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

/** US105 / bridge B3b (command): owner-only write, forwarding, AircraftStatusChanged only when the status changes. */
@ExtendWith(MockitoExtension.class)
class UpdateAircraftStatusServiceTest {

    @Mock private AircraftRepository repository;
    @Mock private PeerClient peerClient;
    @Mock private AircraftManagementEventEmitter events;

    private UpdateAircraftStatusService service;

    @BeforeEach
    void setUp() {
        service = new UpdateAircraftStatusService(repository, peerClient, new AircraftAssembler(), events);
    }

    private static Aircraft aircraft(AircraftStatus status) {
        return new Aircraft("CS-TUA", "A320", 180, LocalDate.of(2020, 5, 17), status);
    }

    @Test
    void ownerChangesTheStatusAndEmitsAircraftStatusChanged() {
        Aircraft a = aircraft(AircraftStatus.AVAILABLE);
        when(repository.findById("CS-TUA")).thenReturn(Optional.of(a));
        when(repository.saveAndFlush(a)).thenReturn(a);

        AircraftDTO result = service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("under_maintenance"));

        assertEquals("UNDER_MAINTENANCE", result.getStatus());
        verify(events).aircraftStatusChanged(a);
        verifyNoInteractions(peerClient);
    }

    @Test
    void sameStatusWritesNothingAndPublishesNothing() {
        Aircraft a = aircraft(AircraftStatus.AVAILABLE);
        when(repository.findById("CS-TUA")).thenReturn(Optional.of(a));

        AircraftDTO result = service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("AVAILABLE"));

        assertEquals("AVAILABLE", result.getStatus());
        verify(repository, never()).saveAndFlush(any());
        verify(events, never()).aircraftStatusChanged(any());
    }

    @Test
    void invalidTransitionIsAConflictAndNothingIsPublished() {
        Aircraft a = aircraft(AircraftStatus.IN_FLIGHT);
        when(repository.findById("CS-TUA")).thenReturn(Optional.of(a));

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("UNDER_MAINTENANCE")));
        verify(events, never()).aircraftStatusChanged(any());
    }

    @Test
    void aircraftOfAnotherReplicaIsForwardedToItsOwner() {
        AircraftDTO fromOwner = new AircraftDTO();
        fromOwner.setRegistrationNumber("CS-TUC");
        fromOwner.setStatus("UNDER_MAINTENANCE");
        UpdateAircraftStatusDTO body = new UpdateAircraftStatusDTO("UNDER_MAINTENANCE");
        when(repository.findById("CS-TUC")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircrafts/CS-TUC/status"), eq(body),
                eq(AircraftDTO.class))).thenReturn(Optional.of(fromOwner));

        assertSame(fromOwner, service.updateStatus("CS-TUC", body));
        verify(events, never()).aircraftStatusChanged(any());   // the owner publishes, not this replica
    }

    @Test
    void unknownEverywhereIsNotFound() {
        UpdateAircraftStatusDTO body = new UpdateAircraftStatusDTO("AVAILABLE");
        when(repository.findById("CS-NONE")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(any(), any(), any(), eq(AircraftDTO.class))).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.updateStatus("CS-NONE", body));
    }

    @Test
    void invalidStatusIsABadRequestBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("FLYING_HIGH")));
        verifyNoInteractions(repository, peerClient, events);
    }
}
