package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.domain.RecordDetails;
import isep.psoft.aisafe.maintenance.domain.TemplateReference;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

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
    @InjectMocks CompleteMaintenanceRecordService service;

    private static final CompleteMaintenanceRecordDTO REQUEST =
            new CompleteMaintenanceRecordDTO("No findings", new BigDecimal("1000"), 500, null, 1200.0);

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
    void localRecordIsCompletedAndTheAircraftReleased() {
        MaintenanceRecord record = record();
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));
        when(repository.saveAndFlush(record)).thenReturn(record);
        when(assembler.toDTO(record)).thenReturn(dto());
        when(availability.releaseIfNoOtherOngoing("CS-TUA", "rec-1")).thenReturn(true);

        CompleteMaintenanceRecordService.Result result = service.complete("rec-1", REQUEST);

        assertEquals(MaintenanceStatus.COMPLETED, record.getStatus());
        assertEquals(LocalDate.now(), record.getCompletion().getCompletionDate()); // por omissão: hoje
        assertTrue(result.aircraftReleased());
        verify(peerClient, never()).forwardToOwner(any(), any(), any(), any());
    }

    @Test
    void recordOfAnotherReplicaIsForwardedToItsOwner() {
        when(repository.findById("rec-1")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(eq(HttpMethod.PATCH), eq("/api/maintenance-records/rec-1/complete"),
                eq(REQUEST), eq(MaintenanceRecordDTO.class))).thenReturn(Optional.of(dto()));
        when(assembler.withLinks(any())).thenAnswer(i -> i.getArgument(0));
        when(availability.releaseIfNoOtherOngoing("CS-TUA", "rec-1")).thenReturn(false);

        CompleteMaintenanceRecordService.Result result = service.complete("rec-1", REQUEST);

        assertEquals("rec-1", result.record().getId());
        assertFalse(result.aircraftReleased());
        verify(repository, never()).saveAndFlush(any());
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
    void unknownRecordIs404AndCompletedRecordIs409() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(peerClient.forwardToOwner(any(), any(), any(), any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.complete("missing", REQUEST));

        MaintenanceRecord record = record();
        record.complete(new isep.psoft.aisafe.maintenance.domain.CompletionDetails(LocalDate.now(), "n", BigDecimal.ONE, 1, null));
        when(repository.findById("rec-1")).thenReturn(Optional.of(record));
        assertThrows(IllegalStateException.class, () -> service.complete("rec-1", REQUEST));
        verifyNoInteractions(availability);
    }
}
