package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.FleetAvailabilityDTO;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** US205 - disponibilidade da aeronave e da frota. */
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
    void availableAircraftIsMarkedAvailable() {
        when(searchService.getByRegistration("CS-TUA")).thenReturn(aircraft("CS-TUA", AircraftStatus.AVAILABLE));

        AircraftAvailabilityDTO dto = service.forAircraft("CS-TUA");

        assertEquals(new AircraftAvailabilityDTO("CS-TUA", "AVAILABLE", true), dto);
    }

    @Test
    void anyOtherStatusIsNotAvailable() {
        for (AircraftStatus s : AircraftStatus.values()) {
            if (s == AircraftStatus.AVAILABLE) {
                continue;
            }
            when(searchService.getByRegistration("CS-TUA")).thenReturn(aircraft("CS-TUA", s));

            AircraftAvailabilityDTO dto = service.forAircraft("CS-TUA");

            assertEquals(s.name(), dto.status());
            assertFalse(dto.available(), "status " + s + " must not be available");
        }
    }

    @Test
    void unknownAircraftPropagatesNotFound() {
        when(searchService.getByRegistration("XX-NONE")).thenThrow(new EntityNotFoundException("Aircraft not found: XX-NONE"));

        assertThrows(EntityNotFoundException.class, () -> service.forAircraft("XX-NONE"));
    }

    @Test
    void fleetCountsEveryStatusEvenWhenZero() {
        when(searchService.search(null, null, null)).thenReturn(List.of(
                aircraft("CS-A", AircraftStatus.AVAILABLE),
                aircraft("CS-B", AircraftStatus.AVAILABLE),
                aircraft("CS-C", AircraftStatus.IN_FLIGHT),
                aircraft("CS-D", AircraftStatus.UNDER_MAINTENANCE)));

        FleetAvailabilityDTO dto = service.forFleet();

        assertEquals(4, dto.total());
        assertEquals(2L, dto.countByStatus().get("AVAILABLE"));
        assertEquals(1L, dto.countByStatus().get("IN_FLIGHT"));
        assertEquals(1L, dto.countByStatus().get("UNDER_MAINTENANCE"));
        assertEquals(0L, dto.countByStatus().get("INACTIVE"));
        assertEquals(List.of("AVAILABLE", "IN_FLIGHT", "UNDER_MAINTENANCE", "INACTIVE"),
                List.copyOf(dto.countByStatus().keySet())); // ordem estável (a do enum)
        assertEquals(0.5, dto.availabilityRate());
    }

    @Test
    void emptyFleetHasZeroRateAndNoDivisionByZero() {
        when(searchService.search(null, null, null)).thenReturn(List.of());

        FleetAvailabilityDTO dto = service.forFleet();

        assertEquals(0, dto.total());
        assertEquals(0.0, dto.availabilityRate());
        assertTrue(dto.countByStatus().values().stream().allMatch(v -> v == 0L));
    }

    @Test
    void rateIsRoundedToTwoDecimals() {
        when(searchService.search(null, null, null)).thenReturn(List.of(
                aircraft("CS-A", AircraftStatus.AVAILABLE),
                aircraft("CS-B", AircraftStatus.INACTIVE),
                aircraft("CS-C", AircraftStatus.INACTIVE)));

        assertEquals(0.33, service.forFleet().availabilityRate()); // 1/3
    }

    @Test
    void fullyAvailableFleetHasRateOne() {
        when(searchService.search(null, null, null)).thenReturn(List.of(
                aircraft("CS-A", AircraftStatus.AVAILABLE), aircraft("CS-B", AircraftStatus.AVAILABLE)));

        assertEquals(1.0, service.forFleet().availabilityRate());
    }
}
