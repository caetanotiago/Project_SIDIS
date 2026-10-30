package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.AircraftDTO;
import isep.psoft.aisafe.maintenance.domain.Maintenance;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRequest;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceReportRequest;
import isep.psoft.aisafe.maintenance.dto.ScheduleMaintenanceRequest;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for maintenance record use cases and replica collaboration.
 *
 * @author Diogo Fernandes - 1240632
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceRecordServiceTest {

    @Mock
    private AircraftClient aircraftClient;

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private PeerClient peerClient;

    @InjectMocks
    private MaintenanceRecordService service;

    @Test
    void startGetsAircraftUpdatesItsStatusAndPersistsRecord() {
        ScheduleMaintenanceRequest request = new ScheduleMaintenanceRequest(
                "CS-TST", LocalDate.now().plusDays(2), "Routine Inspection");
        when(aircraftClient.getAircraft("CS-TST")).thenReturn(aircraft());
        when(maintenanceRepository.saveAndFlush(any(Maintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceRecordDTO record = service.start(request);

        assertEquals("CS-TST", record.aircraftRegistration());
        assertEquals(MaintenanceStatus.SCHEDULED, record.status());
        InOrder order = inOrder(aircraftClient, maintenanceRepository);
        order.verify(aircraftClient).getAircraft("CS-TST");
        order.verify(aircraftClient).updateStatus("CS-TST", "UNDER_MAINTENANCE");
        order.verify(maintenanceRepository).saveAndFlush(any(Maintenance.class));
        verifyNoInteractions(peerClient);
    }

    @Test
    void startRejectsUnknownAircraftWithoutChangingStatusOrSaving() {
        ScheduleMaintenanceRequest request = new ScheduleMaintenanceRequest(
                "CS-TST", LocalDate.now().plusDays(2), "Routine Inspection");
        when(aircraftClient.getAircraft("CS-TST"))
                .thenThrow(new EntityNotFoundException("Aircraft not found: CS-TST"));

        assertThrows(EntityNotFoundException.class, () -> service.start(request));

        verify(aircraftClient, never()).updateStatus(any(), any());
        verifyNoInteractions(maintenanceRepository, peerClient);
    }

    @Test
    void startCompensatesAircraftStatusWhenLocalSaveFails() {
        ScheduleMaintenanceRequest request = new ScheduleMaintenanceRequest(
                "CS-TST", LocalDate.now().plusDays(2), "Routine Inspection");
        RuntimeException saveFailure = new RuntimeException("Database write failed");
        when(aircraftClient.getAircraft("CS-TST")).thenReturn(aircraft());
        when(maintenanceRepository.saveAndFlush(any(Maintenance.class))).thenThrow(saveFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.start(request));

        assertEquals(saveFailure, thrown);
        InOrder order = inOrder(aircraftClient, maintenanceRepository);
        order.verify(aircraftClient).getAircraft("CS-TST");
        order.verify(aircraftClient).updateStatus("CS-TST", "UNDER_MAINTENANCE");
        order.verify(maintenanceRepository).saveAndFlush(any(Maintenance.class));
        order.verify(aircraftClient).updateStatus("CS-TST", "AVAILABLE");
    }

    @Test
    void completeUpdatesAircraftAndSavesCompletedRecord() {
        LocalDate scheduledDate = LocalDate.now().plusDays(1);
        LocalDate completionDate = scheduledDate.plusDays(2);
        Maintenance maintenance = new Maintenance("CS-TST", scheduledDate, "Routine Inspection");
        when(maintenanceRepository.findById("record-1")).thenReturn(Optional.of(maintenance));
        when(maintenanceRepository.saveAndFlush(maintenance)).thenReturn(maintenance);

        MaintenanceRecordDTO response = service.complete(
                "record-1",
                new CompleteMaintenanceRequest(
                        completionDate,
                        new MaintenanceReportRequest("Inspection completed")));

        assertEquals(MaintenanceStatus.COMPLETED, response.status());
        assertEquals(completionDate, response.completionDate());
        InOrder order = inOrder(maintenanceRepository, aircraftClient);
        order.verify(maintenanceRepository).findById("record-1");
        order.verify(aircraftClient).updateStatus("CS-TST", "AVAILABLE");
        order.verify(maintenanceRepository).saveAndFlush(maintenance);
        verifyNoInteractions(peerClient);
    }

    @Test
    void completeRejectsCancelledRecordBeforeUpdatingAircraft() {
        LocalDate scheduledDate = LocalDate.now().plusDays(1);
        Maintenance maintenance = new Maintenance("CS-TST", scheduledDate, "Routine Inspection");
        ReflectionTestUtils.setField(maintenance, "status", MaintenanceStatus.CANCELLED);
        when(maintenanceRepository.findById("record-1")).thenReturn(Optional.of(maintenance));

        assertThrows(IllegalStateException.class, () -> service.complete(
                "record-1",
                new CompleteMaintenanceRequest(
                        scheduledDate,
                        new MaintenanceReportRequest("Inspection completed"))));

        verifyNoInteractions(aircraftClient);
        verify(maintenanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeForwardsNonLocalRecordToOwner() {
        CompleteMaintenanceRequest request = new CompleteMaintenanceRequest(
                LocalDate.now().plusDays(3),
                new MaintenanceReportRequest("Inspection completed"));
        MaintenanceRecordDTO expected = new MaintenanceRecordDTO(
                "record-1", "CS-TST", LocalDate.now().plusDays(1), request.completionDate(),
                "Routine Inspection", "Inspection completed", MaintenanceStatus.COMPLETED);
        when(maintenanceRepository.findById("record-1")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(
                HttpMethod.PATCH,
                "/api/maintenance/record-1/complete",
                request,
                MaintenanceRecordDTO.class))
                .thenReturn(Optional.of(expected));

        assertEquals(expected, service.complete("record-1", request));

        verifyNoInteractions(aircraftClient);
        verify(maintenanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void getByIdSearchesPeersWhenRecordIsNotLocal() {
        MaintenanceRecordDTO expected = new MaintenanceRecordDTO(
                "record-1", "CS-TST", LocalDate.now().plusDays(1), null,
                "Routine Inspection", null, MaintenanceStatus.SCHEDULED);
        when(maintenanceRepository.findById("record-1")).thenReturn(Optional.empty());
        when(peerClient.findFirst("/api/maintenance/record-1", MaintenanceRecordDTO.class))
                .thenReturn(Optional.of(expected));

        assertEquals(expected, service.getById("record-1"));
    }

    @Test
    void getByIdThrowsNotFoundWhenNoReplicaOwnsRecord() {
        when(maintenanceRepository.findById("missing")).thenReturn(Optional.empty());
        when(peerClient.findFirst("/api/maintenance/missing", MaintenanceRecordDTO.class))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getById("missing"));
    }

    @Test
    void historyMergesLocalAndPeerRecordsInDescendingOrder() {
        LocalDate olderDate = LocalDate.now().plusDays(1);
        LocalDate newerDate = LocalDate.now().plusDays(5);
        Maintenance older = new Maintenance("CS-TST", olderDate, "Older service");
        Maintenance newer = new Maintenance("CS-TST", newerDate, "Newer service");
        ReflectionTestUtils.setField(older, "id", "local-older");
        ReflectionTestUtils.setField(newer, "id", "local-newer");
        MaintenanceRecordDTO peerRecord = new MaintenanceRecordDTO(
                "peer-record", "CS-TST", LocalDate.now().plusDays(3), null,
                "Peer service", null, MaintenanceStatus.SCHEDULED);
        when(aircraftClient.getAircraft("CS-TST")).thenReturn(aircraft());
        when(maintenanceRepository.findByAircraftRegistrationOrderByScheduledDateDescIdDesc("CS-TST"))
                .thenReturn(List.of(newer, older));
        when(peerClient.collectList(
                eq("/api/maintenance/aircraft/CS-TST"),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<List<MaintenanceRecordDTO>>>any()))
                .thenReturn(List.of(peerRecord));

        List<MaintenanceRecordDTO> history = service.history("CS-TST");

        assertEquals(List.of("local-newer", "peer-record", "local-older"),
                history.stream().map(MaintenanceRecordDTO::id).toList());
    }

    private static AircraftDTO aircraft() {
        return new AircraftDTO("CS-TST", "A320", 180, "AVAILABLE");
    }
}
