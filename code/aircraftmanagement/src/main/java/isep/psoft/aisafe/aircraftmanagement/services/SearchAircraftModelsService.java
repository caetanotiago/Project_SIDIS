package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Ponte B1b. */
@Service
@RequiredArgsConstructor
public class SearchAircraftModelsService {

    private final AircraftReplicaQueries replicas;

    public AircraftModelSummary getByName(String modelName) {
        return replicas.findModel(modelName)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft model not found: " + modelName));
    }
}
