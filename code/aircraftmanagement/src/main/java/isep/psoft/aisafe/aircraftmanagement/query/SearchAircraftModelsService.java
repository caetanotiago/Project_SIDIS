package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bridges B1b and B3c (query): a model of any replica, from this replica's read model {@code REPLICATED_AIRCRAFT_MODEL}. */
@Service
@RequiredArgsConstructor
public class SearchAircraftModelsService {

    private final ReplicatedAircraftModelRepository models;

    @Transactional(readOnly = true)
    public AircraftModelSummary getByName(String modelName) {
        return models.findById(modelName).map(ReplicatedAircraftModel::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft model not found: " + modelName));
    }
}
