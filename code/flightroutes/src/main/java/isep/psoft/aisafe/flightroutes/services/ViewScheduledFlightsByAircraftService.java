package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.assemblers.ScheduledFlightAssembler;
import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.ScheduledFlightDTO;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.pathSegment;
import static isep.psoft.aisafe.flightroutes.services.RouteReplicaQueries.uri;

/**
 * US213 - Read access to scheduled flights of every replica (per aircraft, paginated, and by id).
 */
@Service
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftService {

    private static final ParameterizedTypeReference<List<ScheduledFlightDTO>> FLIGHT_LIST = new ParameterizedTypeReference<>() { };

    private static final Comparator<ScheduledFlightDTO> BY_SCHEDULE = Comparator
            .comparing(ScheduledFlightDTO::getDate)
            .thenComparing(ScheduledFlightDTO::getTime)
            .thenComparing(ScheduledFlightDTO::getId);

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final AircraftClient aircraftClient;
    private final PeerClient peerClient;
    private final ScheduledFlightAssembler assembler;

    // US213 — paginação feita depois de juntar os voos de todas as réplicas
    @Transactional(readOnly = true)
    public Page<ScheduledFlightDTO> viewByAircraft(String registration, Pageable pageable) {
        // 404 - aircraft must exist
        if (!aircraftClient.exists(registration)) {
            throw new EntityNotFoundException("Aircraft not found: " + registration);
        }
        return Pages.of(search(registration, null, null), pageable);
    }

    /**
     * Voos de uma aeronave em todas as réplicas, opcionalmente filtrados por data/hora
     * (usado também para detetar conflitos de agendamento em US212). Ordenados por data e hora.
     */
    @Transactional(readOnly = true)
    public List<ScheduledFlightDTO> search(String registration, LocalDate date, LocalTime time) {
        List<ScheduledFlight> local = (date != null && time != null)
                ? scheduledFlightRepository.findConflicting(registration, date, time)
                : scheduledFlightRepository.findByAircraftRegistration(registration).stream()
                        .filter(f -> date == null || date.equals(f.getSchedule().getDate()))
                        .filter(f -> time == null || time.equals(f.getSchedule().getTime()))
                        .toList();

        Map<String, ScheduledFlightDTO> merged = new LinkedHashMap<>();
        assembler.toModelList(local).forEach(dto -> merged.put(dto.getId(), dto));
        peerClient.collectList(uri("/api/scheduled-flights/search",
                        "aircraft", registration, "date", date, "time", time), FLIGHT_LIST)
                .forEach(dto -> merged.putIfAbsent(dto.getId(), assembler.withLinks(dto)));

        return merged.values().stream().sorted(BY_SCHEDULE).toList();
    }

    @Transactional(readOnly = true)
    public ScheduledFlightDTO getById(String id) {
        return scheduledFlightRepository.findById(id)
                .map(assembler::toModel)
                .or(() -> peerClient.findFirst("/api/scheduled-flights/" + pathSegment(id), ScheduledFlightDTO.class)
                        .map(assembler::withLinks))
                .orElseThrow(() -> new EntityNotFoundException("Scheduled flight not found: " + id));
    }
}
