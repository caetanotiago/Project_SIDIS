package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US206 - Horas operacionais por aeronave, calculadas a partir dos voos agendados.
 * Exposto para o microsserviço Aircraft Management (substitui o JOIN entre bases de dados).
 */
@Service
@RequiredArgsConstructor
public class CalculateOperationalHoursService {

    private final ScheduledFlightRepository scheduledFlightRepository;

    @Transactional(readOnly = true)
    public Page<AircraftOperationalHoursDTO> calculateOperationalHours(Pageable pageable) {
        return scheduledFlightRepository.findOperationalHoursPerAircraft(pageable);
    }
}
