package isep.psoft.aisafe.maintenance.command;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.dto.CompleteMaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompleteMaintenanceRecordService {

    private final MaintenanceRecordRepository repository;
    private final MaintenanceRecordAssembler assembler;
    private final PeerClient peerClient;
    private final RecordOwnership ownership;
    private final AircraftAvailabilityService availability;
    private final MaintenanceEventEmitter events;
    private final TransactionTemplate transaction;

    public record Result(MaintenanceRecordDTO record, Boolean aircraftReleased) { }

    public Result complete(String id, CompleteMaintenanceRecordDTO dto) {
        Optional<MaintenanceRecord> local = repository.findById(id);
        MaintenanceRecordDTO completed;
        if (local.isPresent()) {
            completed = transaction.execute(status -> completeLocally(id, dto));
        } else {
            completed = peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/maintenance-records/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8)
                                    + "/complete",
                            dto, MaintenanceRecordDTO.class)
                    .map(assembler::withLinks)
                    .orElseThrow(() -> ownership.notExecutedByAnyPeer(id));
        }
        if (peerClient.isPeerRequest()) {
            return new Result(completed, null);
        }
        boolean released = availability.releaseIfNoOtherOngoing(completed.getAircraftRegistration(), id);
        return new Result(completed, released);
    }

    private MaintenanceRecordDTO completeLocally(String id, CompleteMaintenanceRecordDTO dto) {
        MaintenanceRecord record = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
        LocalDate date = dto.completionDate() == null ? LocalDate.now() : dto.completionDate();
        record.complete(new CompletionDetails(date, dto.notes(), dto.cost(), dto.actualDurationMinutes(),
                dto.aircraftFlightHours()));
        MaintenanceRecord saved = repository.saveAndFlush(record);
        events.recordCompleted(saved);
        return assembler.toDTO(saved);
    }
}
