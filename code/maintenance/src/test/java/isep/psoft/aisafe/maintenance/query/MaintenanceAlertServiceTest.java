package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceAlertDTO;
import isep.psoft.aisafe.maintenance.query.external.FleetDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAlertServiceTest {

    @Mock FleetDirectory fleetDirectory;
    @Mock ViewMaintenanceTemplatesService templates;
    @Mock MaintenanceStatisticsService statistics;
    @InjectMocks MaintenanceAlertService service;

    private void fleet(boolean partial, AircraftSnapshot... aircraft) {
        when(fleetDirectory.fleet()).thenReturn(new Fleet(List.of(aircraft), partial));
        when(templates.periodic()).thenReturn(List.of(ReadModelFixtures.template("a-check", "A-Check", 500, 120, "A320")));
    }

    @Test
    void raisesAlertsForApplicableTemplatesAndSkipsInactiveOrOngoing() {
        LocalDate old = LocalDate.now().minusYears(2);
        fleet(false, new AircraftSnapshot("CS-TUA", "A320", old, "AVAILABLE"),
                new AircraftSnapshot("CS-TUB", "A320", old, "INACTIVE"),
                new AircraftSnapshot("CS-TUC", "A320", old, "UNDER_MAINTENANCE"),
                new AircraftSnapshot("CS-TUE", "E190", old, "AVAILABLE"));
        when(fleetDirectory.flightHours()).thenReturn(new FlightHours(Map.of("CS-TUA", 600.0), false));
        LastCompletionDTO ongoing = new LastCompletionDTO("CS-TUC", "a-check", null, null, true);
        when(statistics.lastCompletions()).thenReturn(Map.of(ongoing.key(), ongoing));

        MaintenanceAlertService.AlertResult result = service.checkFleet();

        assertEquals(List.of("CS-TUA|CALENDAR_DAYS", "CS-TUA|FLIGHT_HOURS"),
                result.alerts().stream().map(a -> a.aircraftRegistration() + "|" + a.trigger()).toList());
        assertFalse(result.partial());
    }

    @Test
    void withoutFlightHoursOnlyCalendarAlertsAreComputedAndTheResultIsPartial() {
        fleet(false, new AircraftSnapshot("CS-TUA", "A320", LocalDate.now().minusYears(2), "AVAILABLE"));
        when(fleetDirectory.flightHours()).thenReturn(new FlightHours(Map.of(), true));
        when(statistics.lastCompletions()).thenReturn(Map.of());

        MaintenanceAlertService.AlertResult result = service.checkFleet();

        assertTrue(result.partial());
        assertEquals(List.of("CALENDAR_DAYS"), result.alerts().stream().map(MaintenanceAlertDTO::trigger).toList());
    }
}
