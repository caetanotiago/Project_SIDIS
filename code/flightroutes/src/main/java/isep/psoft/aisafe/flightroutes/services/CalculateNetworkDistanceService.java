package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.dto.NetworkDistanceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * US215 - Calculates the total distance of the network (sum of the distance of the active routes
 * of every replica, without duplicates).
 */
@Service
@RequiredArgsConstructor
public class CalculateNetworkDistanceService {

    private final RouteReplicaQueries replicas;

    public NetworkDistanceDTO calculateTotalDistance() {
        double total = replicas.activeNetwork().stream()
                .mapToDouble(RouteSummary::distance)
                .sum();
        return new NetworkDistanceDTO(total);
    }
}
