package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.domain.RecordDetails;
import isep.psoft.aisafe.maintenance.domain.TemplateReference;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompleteMaintenanceRecordServiceTest {

    @Mock MaintenanceRecordRepository repository;
    @Mock MaintenanceRecordAssembler assembler;
    @Mock PeerClient peerClient;
    @Mock AircraftAvailabilityService availability;
    @Mock MaintenanceEventEmitter events;
    @Mock RecordOwnership ownership;

    private CompleteMaintenanceRecordService service;

    private static final CompleteMaintenanceRecordDTO REQUEST =
            new CompleteMaintenanceRecordDTO("No findings", new BigDecimal("1000"), 500, null, 1200.0);

    @BeforeEach
    void setUp() {
        TransactionTemplate inline = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(null);
            }
        };
        service = new CompleteMaintenanceRecordService(repository, assembler, peerClient, ownership, availability, events, inline);
    }

    private static MaintenanceRecord record() {
        return new MaintenanceRecord("CS-TUA", "A320", new TemplateReference("t", "A-Check", TemplateType.INSPECTION),
                List.of("x"), new RecordDetails("d", LocalDate.now().minusDays(1), 480), null);
    }

    private static MaintenanceRecordDTO dto() {
        MaintenanceRecordDTO dto = new MaintenanceRecordDTO();
        dto.setId("rec-1");
        dto.setAircraftRegistration("CS-TUA");
        dto.setStatus("COMPLETED");
        return dto;
    }

    @Test
    void localRecordIsCompletedTheEventEmittedAndTheAircraftReleased() {
        MaintenanceRecord record = record();
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));
        when(repository.saveAndFlush(record)).thenReturn(record);
        when(assembler.toDTO(record)).thenReturn(dto());
        when(availability.releaseIfNoOtherOngoing("CS-TUA", "rec-1")).thenReturn(true);

        CompleteMaintenanceRecordService.Result result = service.complete("rec-1", REQUEST);

        assertEquals(MaintenanceStatus.COMPLETED, record.getStatus());
        assertEquals(LocalDate.now(), record.getCompletion().getCompletionDate());
        assertTrue(result.aircraftReleased());
        verify(events).recordCompleted(record);
        verify(peerClient, never()).forwardToOwner(any(), any(), any(), any());
    }

    @Test
    void recordOfAnotherReplicaIsForwardedToItsOwnerAndNothingIsEmittedHere() {
        when(repository.findById("rec-1")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/maintenance-records/rec-1/complete"),
                eq(REQUEST), eq(MaintenanceRecordDTO.class))).thenReturn(Optional.of(dto()));
        when(assembler.withLinks(any())).thenAnswer(i -> i.getArgument(0));
        when(availability.releaseIfNoOtherOngoing("CS-TUA", "rec-1")).thenReturn(false);

        CompleteMaintenanceRecordService.Result result = service.complete("rec-1", REQUEST);

        assertEquals("rec-1", result.record().getId());
        assertFalse(result.aircraftReleased());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void onAPeerRequestTheOwnerDoesNotTouchTheAircraft() {
        MaintenanceRecord record = record();
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));
        when(repository.saveAndFlush(record)).thenReturn(record);
        when(assembler.toDTO(record)).thenReturn(dto());
        when(peerClient.isPeerRequest()).thenReturn(true);

        assertNull(service.complete("rec-1", REQUEST).aircraftReleased());
        verifyNoInteractions(availability);
    }

    @Test
    void whenNoPeerExecutesTheCommandOfAKnownRecordItsOwnerIsDown() {
        when(repository.findById("rec-2")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(ownership.notExecutedByAnyPeer("rec-2"))
                .thenReturn(new RemoteServiceUnavailableException("Replica maintenance-2", null));

        assertThrows(RemoteServiceUnavailableException.class, () -> service.complete("rec-2", REQUEST));
        verifyNoInteractions(availability, events);
    }

    @Test
    void unknownRecordIs404AndCompletedRecordIs409() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(ownership.notExecutedByAnyPeer("missing")).thenReturn(new EntityNotFoundException("not found"));
        assertThrows(EntityNotFoundException.class, () -> service.complete("missing", REQUEST));

        MaintenanceRecord record = record();
        record.complete(new CompletionDetails(LocalDate.now(), "n", BigDecimal.ONE, 1, null));
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));
        assertThrows(IllegalStateException.class, () -> service.complete("rec-1", REQUEST));
        verifyNoInteractions(availability, events);
    }
}
