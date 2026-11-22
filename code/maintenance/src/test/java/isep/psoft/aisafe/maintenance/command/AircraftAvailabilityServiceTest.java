package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.query.ReadModelFixtures;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceRecord;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceRecordRepository;
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
    @Mock ReplicatedMaintenanceRecordRepository readModel;
    @InjectMocks AircraftAvailabilityService service;

    private void ongoing(ReplicatedMaintenanceRecord... records) {
        when(readModel.findByAircraftRegistrationIgnoreCaseAndStatus("CS-TUA", "IN_PROGRESS")).thenReturn(List.of(records));
    }

    @Test
    void releasesWhenTheReadModelHasNoOtherOngoingRecordOfTheAircraft() {
        ongoing(ReadModelFixtures.ongoing("rec-1", "CS-TUA", "A320", "t", 60));

        assertTrue(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
        verify(aircraftClient).updateStatus("CS-TUA", "AVAILABLE");
    }

    @Test
    void keepsTheAircraftUnderMaintenanceWhileAnotherRecordOfAnyReplicaIsOngoing() {
        ongoing(ReadModelFixtures.ongoing("rec-2", "CS-TUA", "A320", "t", 60));

        assertFalse(service.releaseIfNoOtherOngoing("CS-TUA", "rec-1"));
        verifyNoInteractions(aircraftClient);
    }

    @Test
    void aFailedReleaseDoesNotFailTheCompletion() {
        ongoing();
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
