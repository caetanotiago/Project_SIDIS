package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.CompatibleRoutes;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.CompatibleRouteDTO;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US203 - rotas compatíveis com uma aeronave. */
@ExtendWith(MockitoExtension.class)
class FindCompatibleRoutesServiceTest {

    @Mock private AircraftReplicaQueries replicas;
    @Mock private FlightRoutesClient flightRoutesClient;

    private FindCompatibleRoutesService service;

    @BeforeEach
    void setUp() {
        service = new FindCompatibleRoutesService(replicas, flightRoutesClient);
    }

    private static AircraftSummary aircraft(int seats) {
        return new AircraftSummary("CS-TUA", "A320", seats, LocalDate.of(2020, 1, 1), AircraftStatus.AVAILABLE);
    }

    private static AircraftModelSummary model() {
        return new AircraftModelSummary("A320", "Airbus", 180, 24210.0, 6150.0, 840.0);
    }

    @Test
    void usesRangeFromModelAndCapacityFromAircraft() {
        // a aeronave tem 150 lugares, o modelo 180: a capacidade enviada tem de ser a da AERONAVE
        CompatibleRoutes expected = new CompatibleRoutes(List.of(new CompatibleRouteDTO()), false);
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.of(aircraft(150)));
        when(replicas.findModel("A320")).thenReturn(Optional.of(model()));
        when(flightRoutesClient.compatibleRoutes(6150.0, 150)).thenReturn(expected);

        assertSame(expected, service.findFor("CS-TUA"));
    }

    @Test
    void unknownAircraftThrowsNotFoundAndNeverCallsFlightRoutes() {
        when(replicas.findAircraft("XX-NONE")).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.findFor("XX-NONE"));

        assertEquals("Aircraft not found: XX-NONE", ex.getMessage());
        verifyNoInteractions(flightRoutesClient);
    }

    @Test
    void missingModelThrowsNotFoundAndNeverCallsFlightRoutes() {
        when(replicas.findAircraft("CS-TUA")).thenReturn(Optional.of(aircraft(180)));
        when(replicas.findModel("A320")).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.findFor("CS-TUA"));

        assertEquals("Aircraft model not found: A320", ex.getMessage());
        verifyNoInteractions(flightRoutesClient);
    }
}
