package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceAlertDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
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

    @Mock AircraftClient aircraftClient;
    @Mock ViewMaintenanceTemplatesService templates;
    @Mock MaintenanceStatisticsService statistics;
    @InjectMocks MaintenanceAlertService service;

    private static MaintenanceTemplateDTO aCheck() {
        MaintenanceTemplateDTO t = new MaintenanceTemplateDTO();
        t.setId("a-check");
        t.setName("A-Check");
        t.setApplicableModels(List.of("A320"));
        t.setFlightHoursInterval(500);
        t.setCalendarDaysInterval(120);
        return t;
    }

    private void fleet(AircraftSnapshot... aircraft) {
        when(aircraftClient.fleet()).thenReturn(new Fleet(List.of(aircraft), false));
        when(templates.list(null, null, null)).thenReturn(List.of(aCheck()));
    }

    @Test
    void raisesAlertsForApplicableTemplatesAndSkipsInactiveOrOngoing() {
        LocalDate old = LocalDate.now().minusYears(2);
        fleet(new AircraftSnapshot("CS-TUA", "A320", old, "AVAILABLE"),
                new AircraftSnapshot("CS-TUB", "A320", old, "INACTIVE"),
                new AircraftSnapshot("CS-TUC", "A320", old, "UNDER_MAINTENANCE"),
                new AircraftSnapshot("CS-TUE", "E190", old, "AVAILABLE"));
        when(aircraftClient.flightHours()).thenReturn(new FlightHours(Map.of("CS-TUA", 600.0), false));
        when(statistics.lastCompletions()).thenReturn(List.of(new LastCompletionDTO("CS-TUC", "a-check", null, null, true)));

        MaintenanceAlertService.AlertResult result = service.checkFleet();

        assertEquals(List.of("CS-TUA|CALENDAR_DAYS", "CS-TUA|FLIGHT_HOURS"),
                result.alerts().stream().map(a -> a.aircraftRegistration() + "|" + a.trigger()).toList());
        assertFalse(result.partial());
    }

    @Test
    void withoutFlightHoursOnlyCalendarAlertsAreComputedAndTheResultIsPartial() {
        fleet(new AircraftSnapshot("CS-TUA", "A320", LocalDate.now().minusYears(2), "AVAILABLE"));
        when(aircraftClient.flightHours()).thenThrow(new RemoteServiceUnavailableException("Aircraft Management", null));
        when(statistics.lastCompletions()).thenReturn(List.of());

        MaintenanceAlertService.AlertResult result = service.checkFleet();

        assertTrue(result.partial());
        assertEquals(List.of("CALENDAR_DAYS"), result.alerts().stream().map(MaintenanceAlertDTO::trigger).toList());
    }
}
