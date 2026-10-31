package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.CostGrouping;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.domain.RecordDetails;
import isep.psoft.aisafe.maintenance.domain.TemplateReference;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.CostReportDTO;
import isep.psoft.aisafe.maintenance.dto.FleetMaintenanceHoursDTO;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.TurnaroundReportDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import isep.sidis.common.peers.PeerResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** As réplicas trocam somas e contagens; médias e horas só depois de somar. */
@ExtendWith(MockitoExtension.class)
class MaintenanceStatisticsServiceTest {

    @Mock MaintenanceRecordRepository repository;
    @Mock PeerClient peerClient;
    @InjectMocks MaintenanceStatisticsService service;

    private static MaintenanceRecord completed(String reg, String model, String cost, int minutes, LocalDate date) {
        MaintenanceRecord r = new MaintenanceRecord(reg, model, new TemplateReference("tpl", "A-Check", TemplateType.INSPECTION),
                List.of("x"), new RecordDetails("d", date, 100), null);
        r.complete(new CompletionDetails(date, "ok", new BigDecimal(cost), minutes, 1000.0));
        return r;
    }

    @Test
    void turnaroundAverageIsComputedAfterAddingThePeers() {
        // local: 2 registos A320 com 450 min de média (900 no total)
        when(repository.findByStatus(MaintenanceStatus.COMPLETED)).thenReturn(List.of(
                completed("CS-TUA", "A320", "100", 400, LocalDate.now()),
                completed("CS-TUA", "A320", "100", 500, LocalDate.now())));
        // peer: 1 registo A320 com 300 min
        when(peerClient.collect(eq(MaintenanceStatisticsService.TURNAROUND_URI), any())).thenReturn(new PeerResult<>(
                List.of(new TurnaroundReportDTO(List.of(TurnaroundReportDTO.TurnaroundItem.of("A320", 1, 300)))), 0, 1));

        TurnaroundReportDTO.TurnaroundItem a320 = service.turnaround().items().get(0);

        assertEquals(3, a320.completedRecords());
        assertEquals(1200, a320.totalMinutes());
        assertEquals(400.0, a320.averageMinutes()); // não 375 (média das médias)
        assertEquals(6.67, a320.averageHours());
    }

    @Test
    void costsAreSummedPerModelAcrossReplicas() {
        when(repository.findByStatus(MaintenanceStatus.COMPLETED)).thenReturn(List.of(
                completed("CS-TUA", "A320", "1000.50", 60, LocalDate.now())));
        when(peerClient.collect(eq("/api/maintenance-records/reports/costs?groupBy=MODEL"), any())).thenReturn(new PeerResult<>(
                List.of(new CostReportDTO("MODEL", new BigDecimal("3000"), 2, List.of(
                        new CostReportDTO.CostItem("A320", new BigDecimal("500"), 1),
                        new CostReportDTO.CostItem("E190", new BigDecimal("2500"), 1)))), 0, 1));

        CostReportDTO report = service.costs(CostGrouping.MODEL);

        assertEquals(new BigDecimal("4000.50"), report.totalCost());
        assertEquals(3, report.records());
        assertEquals("E190", report.items().get(0).key()); // mais caro primeiro
        assertEquals(new BigDecimal("1500.50"), report.items().get(1).totalCost());
    }

    @Test
    void maintenanceHoursUseActualMinutesOfCompletedAndExpectedOfOngoing() {
        MaintenanceRecord ongoing = new MaintenanceRecord("CS-TUA", "A320",
                new TemplateReference("tpl", "A-Check", TemplateType.INSPECTION), List.of("x"),
                new RecordDetails("d", LocalDate.now(), 120), null);
        when(repository.findAll()).thenReturn(List.of(ongoing, completed("CS-TUA", "A320", "1", 60, LocalDate.now())));
        when(peerClient.collect(eq(MaintenanceStatisticsService.HOURS_URI), any())).thenReturn(PeerResult.empty());

        FleetMaintenanceHoursDTO hours = service.maintenanceHours();

        assertEquals(180, hours.totalMinutes());
        assertEquals(3.0, hours.totalHours());
        assertEquals(2, hours.aircraft().get(0).records());
    }

    @Test
    void lastCompletionsKeepTheLatestDateAndTheOngoingFlag() {
        LocalDate older = LocalDate.now().minusDays(30);
        when(repository.findAll()).thenReturn(List.of(completed("CS-TUA", "A320", "1", 60, older)));
        when(peerClient.collect(eq(MaintenanceStatisticsService.LAST_COMPLETIONS_URI), any())).thenReturn(new PeerResult<>(
                List.of(List.of(new LastCompletionDTO("CS-TUA", "tpl", LocalDate.now(), 2000.0, false),
                        new LastCompletionDTO("cs-tua", "tpl", null, null, true))), 0, 1));

        List<LastCompletionDTO> result = service.lastCompletions();

        assertEquals(1, result.size());
        assertEquals(LocalDate.now(), result.get(0).lastCompletionDate());
        assertEquals(2000.0, result.get(0).aircraftFlightHours());
        assertTrue(result.get(0).ongoing());
    }
}
