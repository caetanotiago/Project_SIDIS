package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.dto.NetworkDistanceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US215 (query) - total distance of the network (sum of the distances of the active routes of the read model). */
@Service
@RequiredArgsConstructor
public class CalculateNetworkDistanceService {

    private final ReplicatedRouteRepository routes;

    @Transactional(readOnly = true)
    public NetworkDistanceDTO calculateTotalDistance() {
        double total = routes.findAllActive().stream()
                .mapToDouble(r -> r.getDistance() == null ? 0 : r.getDistance())
                .sum();
        return new NetworkDistanceDTO(total);
    }
}
