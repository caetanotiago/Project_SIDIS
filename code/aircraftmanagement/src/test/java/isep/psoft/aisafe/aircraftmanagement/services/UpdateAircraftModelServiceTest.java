package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
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

/** US201 - atualização parcial de modelos de aeronave. */
@ExtendWith(MockitoExtension.class)
class UpdateAircraftModelServiceTest {

    @Mock private AircraftModelRepository repository;
    @Mock private PeerClient peerClient;

    private UpdateAircraftModelService service;

    @BeforeEach
    void setUp() {
        service = new UpdateAircraftModelService(repository, peerClient, new AircraftAssembler());
    }

    private static AircraftModel model() {
        return new AircraftModel("A320", "Airbus", 180, 24210.0, 6150.0, 840.0);
    }

    private static UpdateAircraftModelDTO patch(String manufacturer, Integer seats, Double fuel,
                                                Double range, Double speed) {
        return new UpdateAircraftModelDTO(manufacturer, seats, fuel, range, speed);
    }

    @Test
    void emptyPatchIsRejectedWithoutAnyLookup() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.update("A320", patch(null, null, null, null, null)));

        assertEquals("At least one field must be provided", ex.getMessage());
        verifyNoInteractions(repository, peerClient);
    }

    @Test
    void updatesOnlyTheProvidedFieldsOfALocalModel() {
        AircraftModel m = model();
        when(repository.findById("A320")).thenReturn(Optional.of(m));
        when(repository.saveAndFlush(m)).thenReturn(m);

        AircraftModelDTO result = service.update("A320", patch(null, null, null, 7000.0, null));

        assertEquals(7000.0, result.getMaximumRange());
        assertEquals("Airbus", result.getManufacturer());
        assertEquals(180, result.getSeatingCapacity());
        verifyNoInteractions(peerClient);
    }

    @Test
    void invalidValueIsRejectedAndNotSaved() {
        when(repository.findById("A320")).thenReturn(Optional.of(model()));

        assertThrows(IllegalArgumentException.class,
                () -> service.update("A320", patch(null, -10, null, null, null)));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void forwardsToOwnerWhenModelIsNotLocal() {
        UpdateAircraftModelDTO body = patch("Boeing", null, null, null, null);
        AircraftModelDTO fromOwner = new AircraftModelDTO();
        fromOwner.setModelName("B737");
        fromOwner.setManufacturer("Boeing");
        when(repository.findById("B737")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircraft-models/B737"),
                eq(body), eq(AircraftModelDTO.class))).thenReturn(Optional.of(fromOwner));

        assertSame(fromOwner, service.update("B737", body));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundWhenNoReplicaOwnsTheModel() {
        UpdateAircraftModelDTO body = patch("Boeing", null, null, null, null);
        when(repository.findById("B737")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/aircraft-models/B737"),
                eq(body), eq(AircraftModelDTO.class))).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.update("B737", body));

        assertEquals("Aircraft model not found: B737", ex.getMessage());
    }
}
