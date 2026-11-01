package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US102 - criação de aeronaves. */
@ExtendWith(MockitoExtension.class)
class CreateAircraftServiceTest {

    private static final LocalDate DATE = LocalDate.of(2020, 5, 17);

    @Mock private AircraftRepository repository;
    @Mock private AircraftReplicaQueries replicas;

    private CreateAircraftService service;

    @BeforeEach
    void setUp() {
        service = new CreateAircraftService(repository, replicas);
    }

    private static CreateAircraftDTO dto(String status) {
        CreateAircraftDTO dto = new CreateAircraftDTO();
        dto.setRegistrationNumber("CS-TUA");
        dto.setModelName("A320");
        dto.setSeatingCapacity(180);
        dto.setManufacturingDate(DATE);
        dto.setStatus(status);
        return dto;
    }

    private static AircraftModelSummary a320() {
        return new AircraftModelSummary("A320", "Airbus", 180, 24210.0, 6150.0, 840.0);
    }

    private void modelExistsAndAircraftIsNew() {
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.empty());
        when(replicas.findModel("A320")).thenReturn(Optional.of(a320()));
        when(repository.save(any(Aircraft.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsAircraftWithGivenStatus() {
        modelExistsAndAircraftIsNew();

        Aircraft created = service.create(dto("under_maintenance"));

        assertEquals("CS-TUA", created.getRegistrationNumber());
        assertEquals("A320", created.getModelName());
        assertEquals(180, created.getSeatingCapacity());
        assertEquals(DATE, created.getManufacturingDate());
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, created.getStatus());
        verify(repository).save(created);
    }

    @Test
    void nullStatusDefaultsToAvailable() {
        modelExistsAndAircraftIsNew();

        assertEquals(AircraftStatus.AVAILABLE, service.create(dto(null)).getStatus());
    }

    @Test
    void blankStatusDefaultsToAvailable() {
        modelExistsAndAircraftIsNew();

        assertEquals(AircraftStatus.AVAILABLE, service.create(dto("   ")).getStatus());
    }

    @Test
    void invalidStatusIsRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.create(dto("FLYING")));

        verifyNoInteractions(replicas, repository);
    }

    @Test
    void duplicateRegistrationInAnyReplicaThrowsIllegalState() {
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.of(
                new AircraftSummary("CS-TUA", "A320", 180, DATE, AircraftStatus.AVAILABLE)));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.create(dto(null)));

        assertEquals("Aircraft already registered: CS-TUA", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void unknownModelThrowsIllegalArgument() {
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.empty());
        when(replicas.findModel("A320")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(dto(null)));

        assertEquals("Aircraft model not found: A320", ex.getMessage());
        verify(repository, never()).save(any());
    }
}
