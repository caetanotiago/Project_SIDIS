package isep.psoft.aisafe.aircraftmanagement.services;

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

import java.util.Optional;

import static isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries.MODELS_URI;
import static isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries.pathSegment;

/**
 * US201. Escrita só na réplica dona do modelo; se não for local, o comando é reencaminhado
 * para o dono (Integration-Contracts §5, "Write to an existing entity").
 */
@Service
@RequiredArgsConstructor
public class UpdateAircraftModelService {

    private final AircraftModelRepository repository;
    private final PeerClient peerClient;
    private final AircraftAssembler assembler;

    @Transactional
    public AircraftModelDTO update(String modelName, UpdateAircraftModelDTO dto) {
        if (dto.isEmpty()) {
            throw new IllegalArgumentException("At least one field must be provided");
        }

        Optional<AircraftModel> local = repository.findById(modelName);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH, MODELS_URI + pathSegment(modelName),
                            dto, AircraftModelDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Aircraft model not found: " + modelName));
        }

        AircraftModel model = local.get();
        model.updateSpecifications(dto.getManufacturer(), dto.getSeatingCapacity(), dto.getFuelCapacity(),
                dto.getMaximumRange(), dto.getCruisingSpeed());
        return assembler.toDTO(repository.saveAndFlush(model)); // @Version → 409 em conflito
    }
}
