package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import isep.sidis.common.peers.PeerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Consultas por identificador sobre TODAS as réplicas do Aircraft Management:
 * primeiro a base local; se não existir, a primeira réplica peer que responda 200
 * (Integration-Contracts §5, "GET by ID"). Num pedido vindo de um peer só responde com dados locais.
 */
@Component
@RequiredArgsConstructor
public class AircraftReplicaQueries {

    static final String AIRCRAFTS_URI = "/api/aircrafts/";
    static final String MODELS_URI = "/api/aircraft-models/";

    private final AircraftRepository aircraftRepository;
    private final AircraftModelRepository modelRepository;
    private final PeerClient peerClient;
    private final AircraftAssembler assembler;

    public Optional<AircraftSummary> findAircraft(String registrationNumber) {
        Optional<AircraftSummary> local = aircraftRepository.findById(registrationNumber)
                .map(a -> a.toSummary());
        if (local.isPresent()) {
            return local;
        }
        return peerClient.findFirst(AIRCRAFTS_URI + pathSegment(registrationNumber), AircraftDTO.class)
                .map(assembler::toSummary);
    }

    public Optional<AircraftModelSummary> findModel(String modelName) {
        Optional<AircraftModelSummary> local = modelRepository.findById(modelName)
                .map(m -> m.toSummary());
        if (local.isPresent()) {
            return local;
        }
        return peerClient.findFirst(MODELS_URI + pathSegment(modelName), AircraftModelDTO.class)
                .map(assembler::toSummary);
    }

    static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
