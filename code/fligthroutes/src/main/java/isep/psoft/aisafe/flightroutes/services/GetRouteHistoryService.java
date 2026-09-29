package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteHistory;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetRouteHistoryService {

    private final FlightRouteRepository routeRepository;

    public List<RouteHistory> getRouteHistory(String routeId) {
        FlightRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + routeId));

        return route.getHistoryLog();
    }
}