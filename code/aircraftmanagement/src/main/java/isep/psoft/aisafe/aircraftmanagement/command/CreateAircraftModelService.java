package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US101 (command) - register an aircraft model. The model is stored in the write model of the replica that received
 * the request, which becomes its owner, and is announced as {@code AircraftModelCreated}.
 *
 * <p>Uniqueness of the model name is checked on the LOCAL read model, which holds the models of every replica (no
 * peer call). Inside the eventual-consistency window (a model created on another replica whose event has not arrived
 * yet) a duplicate can still slip through; see docs/2-containers/Containers.md, section 3.6.
 */
@Service
@RequiredArgsConstructor
public class CreateAircraftModelService {

    private final AircraftModelRepository repository;
    private final ReplicatedAircraftModelRepository readModel;
    private final AircraftManagementEventEmitter events;

    @Transactional
    public AircraftModel create(CreateAircraftModelDTO dto) {
        // 409 - model names are unique in the whole system (read model: every replica)
        if (readModel.existsById(dto.getModelName())) {
            throw new IllegalStateException("Aircraft model already exists: " + dto.getModelName());
        }
        AircraftModel saved = repository.saveAndFlush(new AircraftModel(dto.getModelName(), dto.getManufacturer(),
                dto.getSeatingCapacity(), dto.getFuelCapacity(), dto.getMaximumRange(), dto.getCruisingSpeed()));
        events.aircraftModelCreated(saved);
        return saved;
    }
}
