package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.dto.UpdateRouteDTO;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateFlightRouteService {

    private final FlightRouteRepository routeRepository;

    @Transactional
    public FlightRoute updateRoute(String id, UpdateRouteDTO dto) {

        // Procurar a Rota (404 se não existir)
        FlightRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Flight Route not found: " + id));

        // Atualização parcial dos detalhes operacionais (qualquer subconjunto dos campos).
        if (dto.getMinRange() != null || dto.getMinCapacity() != null || dto.getEstimatedFlightTime() != null) {
            route.updateDetails(dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime());
        }

        // Mudança de estado (ativar/desativar) — pode vir no mesmo pedido que os detalhes.
        if (dto.getStatus() != null) {
            route.changeStatus(dto.getStatus());
        }

        return routeRepository.save(route);
    }
}