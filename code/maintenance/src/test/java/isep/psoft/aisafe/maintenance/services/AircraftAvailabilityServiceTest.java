package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AircraftAvailabilityServiceTest {

    @Mock AircraftClient aircraftClient;
    @Mock MaintenanceReplicaQueries replicas;
    @InjectMocks AircraftAvailabilityService service;

    private static MaintenanceRecordDTO record(String id) {
        MaintenanceRecordDTO dto = new MaintenanceRecordDTO();
        dto.setId(id);
        return dto;
    }

    private void ongoing(boolean complete, MaintenanceRecordDTO... records) {
        when(replicas.searchRecordsWithStatus(RecordFilter.ongoingOf("CS-TUA")))
                .thenReturn(new MaintenanceReplicaQueries.RecordSearch(List.of(records), complete));
    }

    @Test
    void releasesWhenNoOtherRecordIsOngoingInAnyReplica() {
        ongoing(true, record("rec-1")); // o próprio (por segurança é ignorado)

        assertTrue(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
        verify(aircraftClient).updateStatus("CS-TUA", "AVAILABLE");
    }

    @Test
    void keepsTheAircraftUnderMaintenanceWhileAnotherRecordIsOngoing() {
        ongoing(true, record("rec-2"));

        assertFalse(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void keepsTheAircraftUnderMaintenanceWhenAReplicaDidNotAnswer() {
        ongoing(false);

        assertFalse(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void aFailedReleaseDoesNotFailTheCompletion() {
        ongoing(true);
        when(aircraftClient.updateStatus("CS-TUA", "AVAILABLE"))
                .thenThrow(new RemoteServiceUnavailableException("Aircraft Management", null));

        assertFalse(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
    }

    @Test
    void markUnderMaintenanceCallsAircraftManagement() {
        service.markUnderMaintenance(new AircraftSnapshot("CS-TUA", "A320", null, "AVAILABLE"));
        verify(aircraftClient).updateStatus("CS-TUA", "UNDER_MAINTENANCE");

        when(aircraftClient.updateStatus(any(), any())).thenThrow(new RemoteServiceUnavailableException("Aircraft Management", null));
        assertThrows(RemoteServiceUnavailableException.class,
                () -> service.markUnderMaintenance(new AircraftSnapshot("CS-TUA", "A320", null, "AVAILABLE")));
    }
}
