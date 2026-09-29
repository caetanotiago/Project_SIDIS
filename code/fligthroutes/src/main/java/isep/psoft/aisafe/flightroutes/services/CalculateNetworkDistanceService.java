package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.dto.NetworkDistanceDTO;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US215 - Calculates the total distance of the network (sum of active routes' distance).
 */
@Service
@RequiredArgsConstructor
public class CalculateNetworkDistanceService {

    private final FlightRouteRepository flightRouteRepository;

    @Transactional(readOnly = true)
    public NetworkDistanceDTO calculateTotalDistance() {
        return new NetworkDistanceDTO(flightRouteRepository.sumActiveRoutesDistance());
    }
}
