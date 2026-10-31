package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.dto.CategorizeRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** US217. Escrita só na réplica dona; se o registo não é local, o comando é encaminhado. */
@Service
@RequiredArgsConstructor
public class CategorizeMaintenanceRecordService {

    private final MaintenanceRecordRepository repository;
    private final MaintenanceRecordAssembler assembler;
    private final PeerClient peerClient;

    public MaintenanceRecordDTO categorize(String id, CategorizeRecordDTO dto) {
        MaintenanceComponent component = MaintenanceComponent.parse(dto.component()); // 400 antes de encaminhar
        Optional<MaintenanceRecord> local = repository.findById(id);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/maintenance-records/" + MaintenanceReplicaQueries.pathSegment(id) + "/component",
                            dto, MaintenanceRecordDTO.class)
                    .map(assembler::withLinks)
                    .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
        }
        MaintenanceRecord record = local.get();
        record.categorize(component);
        return assembler.toDTO(repository.saveAndFlush(record)); // @Version → 409
    }
}
