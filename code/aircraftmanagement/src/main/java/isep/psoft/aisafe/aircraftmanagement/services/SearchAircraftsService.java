package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** US103 (ponte B1a). */
@Service
@RequiredArgsConstructor
public class SearchAircraftsService {

    private final AircraftReplicaQueries replicas;

    public AircraftSummary getByRegistration(String registrationNumber) {
        return replicas.findAircraft(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
    }
}
