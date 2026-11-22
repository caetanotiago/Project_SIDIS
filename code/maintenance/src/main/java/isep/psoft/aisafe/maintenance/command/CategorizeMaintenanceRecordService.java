package isep.psoft.aisafe.maintenance.command;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategorizeMaintenanceRecordService {

    private final MaintenanceRecordRepository repository;
    private final MaintenanceRecordAssembler assembler;
    private final PeerClient peerClient;
    private final RecordOwnership ownership;
    private final MaintenanceEventEmitter events;

    @Transactional
    public MaintenanceRecordDTO categorize(String id, CategorizeRecordDTO dto) {
        MaintenanceComponent component = MaintenanceComponent.parse(dto.component());
        Optional<MaintenanceRecord> local = repository.findById(id);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/maintenance-records/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8)
                                    + "/component",
                            dto, MaintenanceRecordDTO.class)
                    .map(assembler::withLinks)
                    .orElseThrow(() -> ownership.notExecutedByAnyPeer(id));
        }
        MaintenanceRecord record = local.get();
        record.categorize(component);
        MaintenanceRecord saved = repository.saveAndFlush(record);
        events.recordCategorized(saved);
        return assembler.toDTO(saved);
    }
}
