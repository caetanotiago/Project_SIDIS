package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.MinutesByAircraft;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** US204 - modelos mais utilizados (por minutos de voo). */
@ExtendWith(MockitoExtension.class)
class TopUtilizedModelsServiceTest {

    @Mock private SearchAircraftsService searchService;
    @Mock private FlightRoutesClient flightRoutesClient;

    private TopUtilizedModelsService service;

    @BeforeEach
    void setUp() {
        service = new TopUtilizedModelsService(searchService, flightRoutesClient);
    }

    private static AircraftSummary aircraft(String reg, String model) {
        return new AircraftSummary(reg, model, 180, LocalDate.of(2020, 1, 1), AircraftStatus.AVAILABLE);
    }

    private void fleetAndUsage(List<AircraftSummary> fleet, Map<String, Long> minutes, boolean partial) {
        when(searchService.search(null, null, null)).thenReturn(fleet);
        when(flightRoutesClient.minutesByAircraft()).thenReturn(new MinutesByAircraft(minutes, partial));
    }

    @Test
    void sumsMinutesPerModelAndCountsAircraft() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "A320"), aircraft("CS-C", "B737")),
                Map.of("CS-A", 60L, "CS-B", 30L, "CS-C", 45L), false);

        List<ModelUtilizationDTO> rows = service.topUtilized(10).rows();

        assertEquals(List.of(
                new ModelUtilizationDTO("A320", 90, 1.5, 2),
                new ModelUtilizationDTO("B737", 45, 0.75, 1)), rows);
    }

    @Test
    void sortsByMinutesDescending() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "B737"), aircraft("CS-C", "E190")),
                Map.of("CS-A", 10L, "CS-B", 300L, "CS-C", 100L), false);

        List<String> order = service.topUtilized(10).rows().stream().map(ModelUtilizationDTO::modelName).toList();

        assertEquals(List.of("B737", "E190", "A320"), order);
    }

    @Test
    void tiesAreBrokenByModelNameAscending() {
        fleetAndUsage(List.of(aircraft("CS-A", "B737"), aircraft("CS-B", "A320")),
                Map.of("CS-A", 60L, "CS-B", 60L), false);

        List<String> order = service.topUtilized(10).rows().stream().map(ModelUtilizationDTO::modelName).toList();

        assertEquals(List.of("A320", "B737"), order);
    }

    @Test
    void respectsTheLimit() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "B737"), aircraft("CS-C", "E190")),
                Map.of("CS-A", 10L, "CS-B", 300L, "CS-C", 100L), false);

        List<ModelUtilizationDTO> rows = service.topUtilized(2).rows();

        assertEquals(List.of("B737", "E190"), rows.stream().map(ModelUtilizationDTO::modelName).toList());
    }

    @Test
    void modelsWithoutFlightMinutesAreNotRanked() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "B737")), Map.of("CS-A", 60L), false);

        List<ModelUtilizationDTO> rows = service.topUtilized(10).rows();

        assertEquals(1, rows.size());
        assertEquals("A320", rows.get(0).modelName());
    }

    @Test
    void aircraftWithoutMinutesStillCountTowardsTheirModelAircraftCount() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320"), aircraft("CS-B", "A320")), Map.of("CS-A", 60L), false);

        assertEquals(2, service.topUtilized(10).rows().get(0).aircraftCount());
    }

    @Test
    void emptyUsageGivesEmptyRanking() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320")), Map.of(), false);

        assertTrue(service.topUtilized(10).rows().isEmpty());
    }

    @Test
    void zeroLimitGivesEmptyRanking() {
        fleetAndUsage(List.of(aircraft("CS-A", "A320")), Map.of("CS-A", 60L), false);

        assertTrue(service.topUtilized(0).rows().isEmpty());
    }

    @Test
    void partialFlagIsPropagated() {
        fleetAndUsage(List.of(), Map.of(), true);
        assertTrue(service.topUtilized(5).partial());

        fleetAndUsage(List.of(), Map.of(), false);
        assertFalse(service.topUtilized(5).partial());
    }
}
