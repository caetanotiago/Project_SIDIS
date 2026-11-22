package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * US105 / bridge B3b (command) - change the operational status of an aircraft. ONE OWNER PER ENTITY: executed by the
 * replica that stores the aircraft; otherwise forwarded (unicast) to the owner.
 *
 * <p>B3b stays a synchronous HTTP command (Integration-Contracts section 9.6): Maintenance needs the immediate answer
 * (409 if the transition is invalid). Its EFFECT reaches everyone by event: {@code AircraftStatusChanged} is
 * published only when the status really changed (a PATCH to the same status changes nothing and publishes nothing).
 */
@Service
@RequiredArgsConstructor
public class UpdateAircraftStatusService {

    private final AircraftRepository repository;
    private final PeerClient peerClient;
    private final AircraftAssembler assembler;
    private final AircraftManagementEventEmitter events;

    @Transactional
    public AircraftDTO updateStatus(String registrationNumber, UpdateAircraftStatusDTO dto) {
        AircraftStatus target = AircraftStatus.parse(dto.getStatus());       // 400 if invalid

        Optional<Aircraft> local = repository.findById(registrationNumber);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            "/api/aircrafts/" + UriUtils.encodePathSegment(registrationNumber, StandardCharsets.UTF_8)
                                    + "/status",
                            dto, AircraftDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
        }

        Aircraft aircraft = local.get();
        AircraftStatus previous = aircraft.getStatus();
        aircraft.changeStatus(target);                                       // 409 if the transition is invalid
        if (previous == aircraft.getStatus()) {
            return assembler.toDTO(aircraft);                                // nothing changed: no write, no event
        }

        // Flush first: the event carries the version incremented by this update (@Version -> 409 on conflict).
        Aircraft saved = repository.saveAndFlush(aircraft);
        events.aircraftStatusChanged(saved);
        return assembler.toDTO(saved);
    }
}
