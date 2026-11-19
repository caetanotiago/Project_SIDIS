package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.assemblers.ScheduledFlightAssembler;
import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** US213 (query) - scheduled flights of every replica (per aircraft, paginated, and by id), from the flight read model. */
@Service
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftService {

    private final ReplicatedScheduledFlightRepository flights;
    private final AircraftClient aircraftClient;
    private final ScheduledFlightAssembler assembler;

    @Transactional(readOnly = true)
    public Page<ScheduledFlightDTO> viewByAircraft(String registration, Pageable pageable) {
        // 404 - aircraft must exist (B1)
        if (!aircraftClient.exists(registration)) {
            throw new EntityNotFoundException("Aircraft not found: " + registration);
        }
        return Pages.of(search(registration, null, null), pageable);
    }

    /** Flights of an aircraft, optionally at a date and/or time, sorted by date and time. */
    @Transactional(readOnly = true)
    public List<ScheduledFlightDTO> search(String registration, LocalDate date, LocalTime time) {
        List<ReplicatedScheduledFlight> found = (date != null && time != null)
                ? flights.findConflicting(registration, date, time)
                : flights.findByAircraftRegistration(registration).stream()
                        .filter(f -> date == null || date.equals(f.getDate()))
                        .filter(f -> time == null || time.equals(f.getTime()))
                        .toList();
        return found.stream().map(assembler::toModel).toList();
    }

    @Transactional(readOnly = true)
    public ScheduledFlightDTO getById(String id) {
        return flights.findById(id).map(assembler::toModel)
                .orElseThrow(() -> new EntityNotFoundException("Scheduled flight not found: " + id));
    }
}
