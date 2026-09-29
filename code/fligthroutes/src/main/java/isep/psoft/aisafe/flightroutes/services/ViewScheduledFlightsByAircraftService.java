package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US213 - Read access to scheduled flights (per aircraft, paginated, and by id).
 */
@Service
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftService {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final AircraftClient aircraftClient;

    @Transactional(readOnly = true)
    public Page<ScheduledFlight> viewByAircraft(String registration, Pageable pageable) {
        // 404 - aircraft must exist
        if (!aircraftClient.exists(registration)) {
            throw new EntityNotFoundException("Aircraft not found: " + registration);
        }
        return scheduledFlightRepository.findByAircraftRegistration(registration, pageable);
    }

    @Transactional(readOnly = true)
    public ScheduledFlight getById(String id) {
        return scheduledFlightRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled flight not found: " + id));
    }
}
