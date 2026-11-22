package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.FleetAvailabilityDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** US205 (query). */
@ExtendWith(MockitoExtension.class)
class AircraftAvailabilityServiceTest {

    @Mock private SearchAircraftsService searchService;

    private AircraftAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AircraftAvailabilityService(searchService);
    }

    private static AircraftSummary aircraft(String reg, AircraftStatus status) {
        return new AircraftSummary(reg, "A320", 180, LocalDate.of(2020, 1, 1), status);
    }

    @Test
    void oneAircraftIsAvailableOnlyWhenItsStatusIsAvailable() {
        when(searchService.getByRegistration("CS-A")).thenReturn(aircraft("CS-A", AircraftStatus.AVAILABLE));
        when(searchService.getByRegistration("CS-B")).thenReturn(aircraft("CS-B", AircraftStatus.UNDER_MAINTENANCE));

        AircraftAvailabilityDTO a = service.forAircraft("CS-A");
        AircraftAvailabilityDTO b = service.forAircraft("CS-B");

        assertTrue(a.available());
        assertFalse(b.available());
        assertEquals("UNDER_MAINTENANCE", b.status());
    }

    @Test
    void fleetCountsEveryStatusAndTheAvailabilityRate() {
        when(searchService.fleet()).thenReturn(List.of(aircraft("CS-A", AircraftStatus.AVAILABLE),
                aircraft("CS-B", AircraftStatus.AVAILABLE), aircraft("CS-C", AircraftStatus.INACTIVE),
                aircraft("CS-D", AircraftStatus.UNDER_MAINTENANCE)));

        FleetAvailabilityDTO fleet = service.forFleet();

        assertEquals(4, fleet.total());
        assertEquals(2L, fleet.countByStatus().get("AVAILABLE"));
        assertEquals(0L, fleet.countByStatus().get("IN_FLIGHT"));
        assertEquals(0.5, fleet.availabilityRate());
    }
}
