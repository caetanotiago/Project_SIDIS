package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US102. A aeronave fica guardada na réplica que recebeu o POST. */
@Service
@RequiredArgsConstructor
public class CreateAircraftService {

    private final AircraftRepository repository;
    private final AircraftReplicaQueries replicas;

    @Transactional
    public Aircraft create(CreateAircraftDTO dto) {
        AircraftStatus status = (dto.getStatus() == null || dto.getStatus().isBlank())
                ? AircraftStatus.AVAILABLE
                : AircraftStatus.parse(dto.getStatus());

        // Matrícula única em todas as réplicas (best-effort) → 409
        if (replicas.findAircraft(dto.getRegistrationNumber()).isPresent()) {
            throw new IllegalStateException("Aircraft already registered: " + dto.getRegistrationNumber());
        }
        // O modelo tem de existir (em qualquer réplica) → 400
        if (replicas.findModel(dto.getModelName()).isEmpty()) {
            throw new IllegalArgumentException("Aircraft model not found: " + dto.getModelName());
        }
        return repository.save(new Aircraft(dto.getRegistrationNumber(), dto.getModelName(),
                dto.getSeatingCapacity(), dto.getManufacturingDate(), status));
    }
}
