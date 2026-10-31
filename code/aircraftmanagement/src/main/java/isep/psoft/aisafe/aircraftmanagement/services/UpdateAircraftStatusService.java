package isep.psoft.aisafe.aircraftmanagement.services;

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

import java.util.Optional;

import static isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries.AIRCRAFTS_URI;
import static isep.psoft.aisafe.aircraftmanagement.services.AircraftReplicaQueries.pathSegment;

/**
 * US105 / ponte B3b. Escrita só na réplica dona: se a aeronave não é local, o comando é
 * reencaminhado para o dono (Integration-Contracts §5, "Write to an existing entity").
 */
@Service
@RequiredArgsConstructor
public class UpdateAircraftStatusService {

    private final AircraftRepository repository;
    private final PeerClient peerClient;
    private final AircraftAssembler assembler;

    @Transactional
    public AircraftDTO updateStatus(String registrationNumber, UpdateAircraftStatusDTO dto) {
        AircraftStatus target = AircraftStatus.parse(dto.getStatus()); // 400 se inválido

        Optional<Aircraft> local = repository.findById(registrationNumber);
        if (local.isEmpty()) {
            return peerClient.forwardToOwner(HttpMethod.PATCH,
                            AIRCRAFTS_URI + pathSegment(registrationNumber) + "/status", dto, AircraftDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
        }

        Aircraft aircraft = local.get();
        aircraft.changeStatus(target);                       // 409 se transição inválida
        return assembler.toDTO(repository.saveAndFlush(aircraft)); // @Version → 409 em conflito
    }
}
