package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.CompatibleRoutes;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * US203. Alcance = maximumRange do MODELO; capacidade = seatingCapacity da AERONAVE (o mesmo modelo
 * pode ter configurações de lugares diferentes). A aeronave e o modelo podem estar em qualquer réplica.
 */
@Service
@RequiredArgsConstructor
public class FindCompatibleRoutesService {

    private final AircraftReplicaQueries replicas;
    private final FlightRoutesClient flightRoutesClient;

    public CompatibleRoutes findFor(String registrationNumber) {
        AircraftSummary aircraft = replicas.findAircraft(registrationNumber)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
        AircraftModelSummary model = replicas.findModel(aircraft.modelName())
                .orElseThrow(() -> new EntityNotFoundException("Aircraft model not found: " + aircraft.modelName()));
        return flightRoutesClient.compatibleRoutes(model.maximumRange(), aircraft.seatingCapacity());
    }
}
