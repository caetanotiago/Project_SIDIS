package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US101. O modelo fica guardado na réplica que recebeu o POST (essa réplica é a "dona"). */
@Service
@RequiredArgsConstructor
public class CreateAircraftModelService {

    private final AircraftModelRepository repository;
    private final AircraftReplicaQueries replicas;

    @Transactional
    public AircraftModel create(CreateAircraftModelDTO dto) {
        // Unicidade verificada em todas as réplicas (best-effort: se um peer estiver em baixo, continua)
        if (replicas.findModel(dto.getModelName()).isPresent()) {
            throw new IllegalStateException("Aircraft model already exists: " + dto.getModelName());
        }
        return repository.save(new AircraftModel(dto.getModelName(), dto.getManufacturer(),
                dto.getSeatingCapacity(), dto.getFuelCapacity(), dto.getMaximumRange(), dto.getCruisingSpeed()));
    }
}
