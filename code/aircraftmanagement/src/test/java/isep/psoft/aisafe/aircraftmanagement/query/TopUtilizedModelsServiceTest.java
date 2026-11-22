package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.ModelUtilizationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** US204 (query): read models only (fleet + flight minutes from Flight Routes events). */
@ExtendWith(MockitoExtension.class)
class TopUtilizedModelsServiceTest {

    @Mock private SearchAircraftsService searchService;
    @Mock private FlightMinutesService flightMinutes;

    private TopUtilizedModelsService service;

    @BeforeEach
    void setUp() {
        service = new TopUtilizedModelsService(searchService, flightMinutes);
    }

    private static AircraftSummary aircraft(String reg, String model) {
        return new AircraftSummary(reg, model, 180, LocalDate.of(2020, 1, 1), AircraftStatus.AVAILABLE);
    }

    @Test
    void sumsMinutesPerModelSortsDescendingAndSkipsModelsWithoutFlights() {
        when(searchService.fleet()).thenReturn(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "A320"),
                aircraft("CS-C", "B737"), aircraft("CS-D", "E190")));
        when(flightMinutes.minutesByAircraft()).thenReturn(Map.of("CS-A", 60L, "CS-B", 30L, "CS-C", 120L));

        List<ModelUtilizationDTO> top = service.topUtilized(5);

        assertEquals(2, top.size());
        assertEquals("B737", top.get(0).modelName());
        assertEquals(120L, top.get(0).totalMinutes());
        assertEquals("A320", top.get(1).modelName());
        assertEquals(90L, top.get(1).totalMinutes());
        assertEquals(2, top.get(1).aircraftCount());
    }

    @Test
    void limitIsApplied() {
        when(searchService.fleet()).thenReturn(List.of(aircraft("CS-A", "A320"), aircraft("CS-C", "B737")));
        when(flightMinutes.minutesByAircraft()).thenReturn(Map.of("CS-A", 60L, "CS-C", 120L));

        assertEquals(1, service.topUtilized(1).size());
    }
}
