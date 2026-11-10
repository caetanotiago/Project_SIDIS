package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.dto.ReplicatedRouteDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reads the event-built read model of THIS replica only (no peer calls), to compare it with the aggregated GETs. */
@Service
@RequiredArgsConstructor
public class ReplicatedRouteQueryService {

    private final ReplicatedRouteRepository repository;

    @Transactional(readOnly = true)
    public List<ReplicatedRouteDTO> findAll() {
        return repository.findAllOrdered().stream().map(ReplicatedRouteDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public ReplicatedRouteDTO findById(String id) {
        return repository.findById(id).map(ReplicatedRouteDTO::of)
                .orElseThrow(() -> new EntityNotFoundException("Route not (yet) replicated to this instance: " + id));
    }
}
