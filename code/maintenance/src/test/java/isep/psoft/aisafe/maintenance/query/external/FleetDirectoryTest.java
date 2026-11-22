package isep.psoft.aisafe.maintenance.query.external;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.events.external.AircraftPayload;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetDirectoryTest {

    @Mock KnownAircraftRepository aircraft;
    @Mock KnownAircraftModelRepository models;
    @Mock KnownScheduledFlightRepository flights;
    @Mock ExternalSyncStateRepository syncState;
    @Mock AircraftClient aircraftClient;
    @InjectMocks FleetDirectory directory;

    private static KnownAircraft known(String reg, String status) {
        KnownAircraft row = new KnownAircraft(reg);
        row.apply(new AircraftPayload(reg, "A320", LocalDate.of(2019, 5, 14), status, 3L), Instant.now());
        return row;
    }

    @Test
    void anAircraftInTheLocalCopyIsAnsweredWithoutCallingAircraftManagement() {
        when(aircraft.findByRegistrationNumberIgnoreCase("CS-TUA")).thenReturn(Optional.of(known("CS-TUA", "AVAILABLE")));

        AircraftSnapshot snapshot = directory.aircraft("CS-TUA");

        assertEquals("A320", snapshot.modelName());
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void anUnknownAircraftIsReadOverHttpOnceAndRemembered() {
        AircraftSnapshot remote = new AircraftSnapshot("CS-TUC", "A330", null, "AVAILABLE");
        when(aircraft.findByRegistrationNumberIgnoreCase("CS-TUC")).thenReturn(Optional.empty());
        when(aircraftClient.getAircraft("CS-TUC")).thenReturn(remote);

        assertEquals(remote, directory.aircraft("CS-TUC"));
        verify(aircraft).save(any(KnownAircraft.class));
    }

    @Test
    void theFleetIsSeededOnceOverHttpAndThenReadLocally() {
        when(syncState.existsById(ExternalSyncState.FLEET)).thenReturn(false);
        when(aircraftClient.fleet()).thenReturn(new Fleet(List.of(new AircraftSnapshot("CS-TUA", "A320", null, "AVAILABLE")), false));
        when(aircraft.findByRegistrationNumberIgnoreCase("CS-TUA")).thenReturn(Optional.empty());
        when(aircraft.findAllByOrderByRegistrationNumberAsc()).thenReturn(List.of(known("CS-TUA", "AVAILABLE")));

        Fleet fleet = directory.fleet();

        assertEquals(1, fleet.aircraft().size());
        assertFalse(fleet.partial());
        verify(syncState).save(any(ExternalSyncState.class));
    }

    @Test
    void withoutSeedAndWithAircraftManagementDownTheLocalCopyIsPartial() {
        when(syncState.existsById(ExternalSyncState.FLEET)).thenReturn(false);
        when(aircraftClient.fleet()).thenThrow(new RemoteServiceUnavailableException("Aircraft Management", null));
        when(aircraft.findAllByOrderByRegistrationNumberAsc()).thenReturn(List.of(known("CS-TUA", "AVAILABLE")));

        assertTrue(directory.fleet().partial());
    }

    @Test
    void flightHoursComeFromTheFlightRoutesEventsWhenAnyWasReceived() {
        when(flights.count()).thenReturn(2L);
        when(flights.sumMinutesByAircraft()).thenReturn(List.<Object[]>of(new Object[]{"CS-TUA", 90L}));

        FlightHours hours = directory.flightHours();

        assertEquals(1.5, hours.of("CS-TUA"));
        assertFalse(hours.partial());
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void withoutFlightEventsTheHoursAreAskedToAircraftManagement() {
        when(flights.count()).thenReturn(0L);
        when(aircraftClient.flightHours()).thenReturn(new FlightHours(Map.of("CS-TUA", 12.0), false));

        assertEquals(12.0, directory.flightHours().of("CS-TUA"));
    }
}
