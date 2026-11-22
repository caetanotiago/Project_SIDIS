package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.query.ReadModelFixtures;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceRecordRepository;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordOwnershipTest {

    @Mock ReplicatedMaintenanceRecordRepository readModel;
    @InjectMocks RecordOwnership ownership;

    @Test
    void aRecordKnownByTheReadModelMeansItsOwnerIsUnavailable() {
        when(readModel.findById("rec-1")).thenReturn(Optional.of(
                ReadModelFixtures.ongoing("rec-1", "CS-TUA", "A320", "t", 60)));

        RuntimeException e = ownership.notExecutedByAnyPeer("rec-1");

        assertInstanceOf(RemoteServiceUnavailableException.class, e);
        assertTrue(e.getMessage().contains("maintenance-1"));
    }

    @Test
    void anUnknownRecordIsNotFound() {
        when(readModel.findById("x")).thenReturn(Optional.empty());

        assertInstanceOf(EntityNotFoundException.class, ownership.notExecutedByAnyPeer("x"));
    }
}
