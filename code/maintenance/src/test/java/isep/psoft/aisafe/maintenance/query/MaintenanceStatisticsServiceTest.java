package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.domain.CostGrouping;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static isep.psoft.aisafe.maintenance.query.ReadModelFixtures.completed;
import static isep.psoft.aisafe.maintenance.query.ReadModelFixtures.ongoing;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceStatisticsServiceTest {

    @Mock ReplicatedMaintenanceRecordRepository readModel;
    @InjectMocks MaintenanceStatisticsService service;

    @Test
    void turnaroundIsTheTotalOfMinutesDividedByTheRecordsOfEveryReplica() {
        when(readModel.findByStatus("COMPLETED")).thenReturn(List.of(
                completed("r1", "CS-TUA", "A320", "t", "100", 400, LocalDate.now(), null),
                completed("r2", "CS-TUA", "A320", "t", "100", 500, LocalDate.now(), null),
                completed("r3", "CS-TUB", "A320", "t", "100", 300, LocalDate.now(), null)));

        TurnaroundReportDTO.TurnaroundItem a320 = service.turnaround().items().get(0);

        assertEquals(3, a320.completedRecords());
        assertEquals(1200, a320.totalMinutes());
        assertEquals(400.0, a320.averageMinutes());
        assertEquals(6.67, a320.averageHours());
    }

    @Test
    void costsAreGroupedByModelOrAircraft() {
        when(readModel.findByStatus("COMPLETED")).thenReturn(List.of(
                completed("r1", "CS-TUA", "A320", "t", "1000.50", 60, LocalDate.now(), null),
                completed("r2", "CS-TUA", "A320", "t", "500", 60, LocalDate.now(), null),
                completed("r3", "CS-TUE", "E190", "t", "2500", 60, LocalDate.now(), null)));

        CostReportDTO byModel = service.costs(CostGrouping.MODEL);

        assertEquals(new BigDecimal("4000.50"), byModel.totalCost());
        assertEquals(3, byModel.records());
        assertEquals("E190", byModel.items().get(0).key());
        assertEquals(new BigDecimal("1500.50"), byModel.items().get(1).totalCost());
        assertEquals("CS-TUE", service.costs(CostGrouping.AIRCRAFT).items().get(0).key());
    }

    @Test
    void maintenanceHoursUseActualMinutesOfCompletedAndExpectedOfOngoing() {
        when(readModel.findAll()).thenReturn(List.of(ongoing("r1", "CS-TUA", "A320", "t", 120),
                completed("r2", "CS-TUA", "A320", "t", "1", 60, LocalDate.now(), null)));

        FleetMaintenanceHoursDTO hours = service.maintenanceHours();

        assertEquals(180, hours.totalMinutes());
        assertEquals(3.0, hours.totalHours());
        assertEquals(2, hours.aircraft().get(0).records());
    }

    @Test
    void lastCompletionsKeepTheLatestDateAndTheOngoingFlag() {
        when(readModel.findAll()).thenReturn(List.of(
                completed("r1", "CS-TUA", "A320", "tpl", "1", 60, LocalDate.now().minusDays(30), 1000.0),
                completed("r2", "CS-TUA", "A320", "tpl", "1", 60, LocalDate.now(), 2000.0),
                ongoing("r3", "CS-TUA", "A320", "tpl", 60)));

        Map<String, LastCompletionDTO> result = service.lastCompletions();

        LastCompletionDTO last = result.get(LastCompletionDTO.key("CS-TUA", "tpl"));
        assertEquals(1, result.size());
        assertEquals(LocalDate.now(), last.lastCompletionDate());
        assertEquals(2000.0, last.aircraftFlightHours());
        assertTrue(last.ongoing());
    }
}
