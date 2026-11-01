package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US101 - criação de modelos de aeronave. */
@ExtendWith(MockitoExtension.class)
class CreateAircraftModelServiceTest {

    @Mock private AircraftModelRepository repository;
    @Mock private AircraftReplicaQueries replicas;

    private CreateAircraftModelService service;

    @BeforeEach
    void setUp() {
        service = new CreateAircraftModelService(repository, replicas);
    }

    private static CreateAircraftModelDTO dto() {
        CreateAircraftModelDTO dto = new CreateAircraftModelDTO();
        dto.setModelName("A320");
        dto.setManufacturer("Airbus");
        dto.setSeatingCapacity(180);
        dto.setFuelCapacity(24210.0);
        dto.setMaximumRange(6150.0);
        dto.setCruisingSpeed(840.0);
        return dto;
    }

    @Test
    void createsAndSavesModelWhenNameIsFree() {
        when(replicas.findModel("A320")).thenReturn(Optional.empty());
        when(repository.save(any(AircraftModel.class))).thenAnswer(inv -> inv.getArgument(0));

        AircraftModel created = service.create(dto());

        assertEquals(new AircraftModelSummary("A320", "Airbus", 180, 24210.0, 6150.0, 840.0), created.toSummary());
        verify(repository).save(created);
    }

    @Test
    void existingModelInAnyReplicaThrowsIllegalState() {
        when(replicas.findModel("A320")).thenReturn(Optional.of(
                new AircraftModelSummary("A320", "Airbus", 180, 1.0, 1.0, 1.0)));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.create(dto()));

        assertEquals("Aircraft model already exists: A320", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void invalidValuesAreRejectedByDomainAndNothingIsSaved() {
        CreateAircraftModelDTO invalid = dto();
        invalid.setMaximumRange(-1.0);
        when(replicas.findModel("A320")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));

        verify(repository, never()).save(any());
    }
}
