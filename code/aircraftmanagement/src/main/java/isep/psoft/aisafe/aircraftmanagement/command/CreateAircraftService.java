package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US102 (command) - register an aircraft. The aircraft is stored in the write model of the replica that received the
 * request (its owner) and announced as {@code AircraftRegistered}.
 *
 * <p>Both checks that need the data of every replica (registration unique, model exists) read this replica's read
 * models, so no peer is called. Same consistency window as {@link CreateAircraftModelService}.
 */
@Service
@RequiredArgsConstructor
public class CreateAircraftService {

    private final AircraftRepository repository;
    private final ReplicatedAircraftRepository aircraftReadModel;
    private final ReplicatedAircraftModelRepository modelReadModel;
    private final AircraftManagementEventEmitter events;

    @Transactional
    public Aircraft create(CreateAircraftDTO dto) {
        AircraftStatus status = (dto.getStatus() == null || dto.getStatus().isBlank())
                ? AircraftStatus.AVAILABLE
                : AircraftStatus.parse(dto.getStatus());                     // 400 if invalid

        // 409 - registration numbers are unique in the whole system (read model: every replica)
        if (aircraftReadModel.existsById(dto.getRegistrationNumber())) {
            throw new IllegalStateException("Aircraft already registered: " + dto.getRegistrationNumber());
        }
        // 400 - the model must exist (in any replica)
        if (!modelReadModel.existsById(dto.getModelName())) {
            throw new IllegalArgumentException("Aircraft model not found: " + dto.getModelName());
        }

        Aircraft saved = repository.saveAndFlush(new Aircraft(dto.getRegistrationNumber(), dto.getModelName(),
                dto.getSeatingCapacity(), dto.getManufacturingDate(), status));
        events.aircraftRegistered(saved);
        return saved;
    }
}
