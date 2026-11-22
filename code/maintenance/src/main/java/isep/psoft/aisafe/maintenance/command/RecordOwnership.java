package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceRecordRepository;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordOwnership {

    private final ReplicatedMaintenanceRecordRepository readModel;

    public RuntimeException notExecutedByAnyPeer(String id) {
        return readModel.findById(id)
                .<RuntimeException>map(r -> new RemoteServiceUnavailableException(
                        "Replica " + r.getOwnerInstance() + " (owner of maintenance record " + id + ")", null))
                .orElseGet(() -> new EntityNotFoundException("Maintenance record not found: " + id));
    }
}
