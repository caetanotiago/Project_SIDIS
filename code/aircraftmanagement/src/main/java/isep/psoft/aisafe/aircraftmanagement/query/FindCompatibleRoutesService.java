package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.CompatibleRouteDTO;
import isep.psoft.aisafe.aircraftmanagement.query.external.KnownRoute;
import isep.psoft.aisafe.aircraftmanagement.query.external.KnownRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * US203 (query). Range = {@code maximumRange} of the MODEL; capacity = {@code seatingCapacity} of the AIRCRAFT (the
 * same model can have different seat configurations). The aircraft and the model come from this replica's read
 * models; the routes from {@code KNOWN_ROUTE}, fed by Flight Routes' route events (replaces bridge B6). The same rule
 * as Flight Routes: ACTIVE routes with {@code minRange <= range} and {@code minCapacity <= capacity}.
 */
@Service
@RequiredArgsConstructor
public class FindCompatibleRoutesService {

    private final SearchAircraftsService aircraft;
    private final SearchAircraftModelsService models;
    private final KnownRouteRepository routes;

    @Transactional(readOnly = true)
    public List<CompatibleRouteDTO> findFor(String registrationNumber) {
        AircraftSummary a = aircraft.getByRegistration(registrationNumber);   // 404
        AircraftModelSummary model = models.getByName(a.modelName());         // 404
        return routes.findCompatibleRoutes(model.maximumRange(), a.seatingCapacity()).stream()
                .map(FindCompatibleRoutesService::toDTO)
                .toList();
    }

    /** Same JSON as Flight Routes' {@code FlightRouteDTO} returned by B6, so clients see no change. */
    private static CompatibleRouteDTO toDTO(KnownRoute r) {
        CompatibleRouteDTO dto = new CompatibleRouteDTO();
        dto.setId(r.getRouteId());
        dto.setOriginIATA(r.getOriginIata());
        dto.setDestIATA(r.getDestinationIata());
        dto.setDistance(r.getDistance());
        dto.setMinRange(r.getMinRange());
        dto.setMinCapacity(r.getMinCapacity());
        dto.setEstimatedFlightTime(r.getEstimatedFlightTime());
        dto.setStatus(r.getStatus());
        return dto;
    }
}
