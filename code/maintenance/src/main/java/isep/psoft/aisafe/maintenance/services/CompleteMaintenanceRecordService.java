package isep.psoft.aisafe.maintenance.services;

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

import java.time.LocalDate;
import java.util.Optional;

/**
 * US119. O registo só é escrito pela réplica dona (local, senão o comando é encaminhado). Depois de
 * concluído, a réplica que recebeu o pedido do cliente tenta libertar a aeronave (precisa de
 * consultar todas as réplicas, o que uma réplica a responder a um peer não pode fazer).
 */
@Service
@RequiredArgsConstructor
public class CompleteMaintenanceRecordService {

    private final MaintenanceRecordRepository repository;
    private final MaintenanceRecordAssembler assembler;
    private final PeerClient peerClient;
    private final AircraftAvailabilityService availability;

    /** Registo concluído + se a aeronave voltou a AVAILABLE (null num pedido de um peer). */
    public record Result(MaintenanceRecordDTO record, Boolean aircraftReleased) { }

    public Result complete(String id, CompleteMaintenanceRecordDTO dto) {
        Optional<MaintenanceRecord> local = repository.findById(id);
        MaintenanceRecordDTO completed;
        if (local.isPresent()) {
            completed = completeLocally(local.get(), dto);
        } else {
            completed = peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/maintenance-records/" + MaintenanceReplicaQueries.pathSegment(id) + "/complete",
                            dto, MaintenanceRecordDTO.class)
                    .map(assembler::withLinks)
                    .orElseThrow(() -> new EntityNotFoundException("Maintenance record not found: " + id));
        }

        if (peerClient.isPeerRequest()) {
            return new Result(completed, null); // a réplica que recebeu o pedido trata da aeronave
        }
        boolean released = availability.releaseIfNoOtherOngoing(completed.getAircraftRegistration(), id);
        return new Result(completed, released);
    }

    private MaintenanceRecordDTO completeLocally(MaintenanceRecord record, CompleteMaintenanceRecordDTO dto) {
        LocalDate date = dto.completionDate() == null ? LocalDate.now() : dto.completionDate();
        record.complete(new CompletionDetails(date, dto.notes(), dto.cost(), dto.actualDurationMinutes(),
                dto.aircraftFlightHours()));
        return assembler.toDTO(repository.saveAndFlush(record)); // @Version → 409 em atualização concorrente
    }
}
