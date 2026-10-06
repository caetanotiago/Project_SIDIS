package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * US206 - Horas operacionais por aeronave, calculadas a partir dos voos agendados de todas as
 * réplicas. Exposto para o microsserviço Aircraft Management (substitui o JOIN entre bases de dados).
 * Só aparecem aeronaves com voos; o Aircraft Management assume 0 para as restantes.
 */
@Service
@RequiredArgsConstructor
public class CalculateOperationalHoursService {

    private final ScheduledFlightStatisticsService statistics;

    public Page<AircraftOperationalHoursDTO> calculateOperationalHours(Pageable pageable) {
        List<AircraftOperationalHoursDTO> all = statistics.minutesByAircraft().entrySet().stream()
                .map(e -> new AircraftOperationalHoursDTO(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(AircraftOperationalHoursDTO::getRegistrationNumber))
                .toList();
        return Pages.of(all, pageable);
    }
}
