package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * US201 (command) - update the specifications of a model. ONE OWNER PER ENTITY: the write is always executed by the
 * replica that stores the model in its write model; if it is not local, the command is forwarded (unicast) to the
 * peer that owns it. The owner's {@code @Version} detects concurrent writes (409) and the change is announced as
 * {@code AircraftModelUpdated}, so every read model (this service's replicas, Flight Routes) gets the new
 * {@code maximumRange}.
 */
@Service
@RequiredArgsConstructor
public class UpdateAircraftModelService {

    private final AircraftModelRepository repository;
    private final PeerClient peerClient;
    private final AircraftAssembler assembler;
    private final AircraftManagementEventEmitter events;

    @Transactional
    public AircraftModelDTO update(String modelName, UpdateAircraftModelDTO dto) {
        if (dto.isEmpty()) {
            throw new IllegalArgumentException("At least one field must be provided");
        }

        Optional<AircraftModel> local = repository.findById(modelName);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/aircraft-models/" + UriUtils.encodePathSegment(modelName, StandardCharsets.UTF_8),
                            dto, AircraftModelDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Aircraft model not found: " + modelName));
        }

        AircraftModel model = local.get();
        model.updateSpecifications(dto.getManufacturer(), dto.getSeatingCapacity(), dto.getFuelCapacity(),
                dto.getMaximumRange(), dto.getCruisingSpeed());

        // Flush first: the event carries the version incremented by this update (@Version -> 409 on conflict).
        AircraftModel saved = repository.saveAndFlush(model);
        events.aircraftModelUpdated(saved);
        return assembler.toDTO(saved);
    }
}
