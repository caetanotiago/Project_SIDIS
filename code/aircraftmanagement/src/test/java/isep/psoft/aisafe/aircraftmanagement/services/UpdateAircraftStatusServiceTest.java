package isep.psoft.aisafe.aircraftmanagement.services;

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

/** US105 / ponte B3b - alteração do estado de uma aeronave. */
@ExtendWith(MockitoExtension.class)
class UpdateAircraftStatusServiceTest {

    @Mock private AircraftRepository repository;
    @Mock private PeerClient peerClient;

    private UpdateAircraftStatusService service;

    @BeforeEach
    void setUp() {
        service = new UpdateAircraftStatusService(repository, peerClient, new AircraftAssembler());
    }

    private static Aircraft aircraft(AircraftStatus status) {
        return new Aircraft("CS-TUA", "A320", 180, LocalDate.of(2020, 5, 17), status);
    }

    @Test
    void updatesLocalAircraftAndReturnsDto() {
        Aircraft a = aircraft(AircraftStatus.AVAILABLE);
        when(repository.findById("CS-TUA")).thenReturn(Optional.of(a));
        when(repository.saveAndFlush(a)).thenReturn(a);

        AircraftDTO result = service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("under_maintenance"));

        assertEquals("UNDER_MAINTENANCE", result.getStatus());
        assertEquals("CS-TUA", result.getRegistrationNumber());
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, a.getStatus());
        verifyNoInteractions(peerClient);
    }

    @Test
    void invalidStatusIsRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("FLYING")));

        verifyNoInteractions(repository, peerClient);
    }

    @Test
    void inFlightAircraftCannotGoToMaintenance() {
        Aircraft a = aircraft(AircraftStatus.IN_FLIGHT);
        when(repository.findById("CS-TUA")).thenReturn(Optional.of(a));

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus("CS-TUA", new UpdateAircraftStatusDTO("UNDER_MAINTENANCE")));

        assertEquals(AircraftStatus.IN_FLIGHT, a.getStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void forwardsToOwnerWhenAircraftIsNotLocal() {
        UpdateAircraftStatusDTO body = new UpdateAircraftStatusDTO("INACTIVE");
        AircraftDTO fromOwner = new AircraftDTO();
        fromOwner.setRegistrationNumber("CS-TUA");
        fromOwner.setStatus("INACTIVE");
        when(repository.findById("CS-TUA")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircrafts/CS-TUA/status"),
                eq(body), eq(AircraftDTO.class))).thenReturn(Optional.of(fromOwner));

        AircraftDTO result = service.updateStatus("CS-TUA", body);

        assertSame(fromOwner, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundWhenNoReplicaOwnsTheAircraft() {
        UpdateAircraftStatusDTO body = new UpdateAircraftStatusDTO("INACTIVE");
        when(repository.findById("XX-NONE")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircrafts/XX-NONE/status"),
                eq(body), eq(AircraftDTO.class))).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> service.updateStatus("XX-NONE", body));

        assertEquals("Aircraft not found: XX-NONE", ex.getMessage());
    }
}
