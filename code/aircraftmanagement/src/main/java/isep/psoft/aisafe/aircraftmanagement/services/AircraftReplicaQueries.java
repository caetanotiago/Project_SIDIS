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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas sobre TODAS as réplicas do Aircraft Management (Integration-Contracts §5):
 * por ID → local, senão a primeira réplica peer que responda 200;
 * coleção → dados locais + o mesmo GET em cada peer, juntos por matrícula (sem duplicados).
 * Num pedido vindo de um peer só se responde com dados locais.
 */
@Component
@RequiredArgsConstructor
public class AircraftReplicaQueries {

    static final String AIRCRAFTS_URI = "/api/aircrafts/";
    static final String MODELS_URI = "/api/aircraft-models/";
    static final ParameterizedTypeReference<List<AircraftDTO>> AIRCRAFT_LIST = new ParameterizedTypeReference<>() { };

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

    /** Junta os resultados locais com os dos peers (mesmo endpoint e filtros), sem duplicados. */
    public List<AircraftSummary> withPeers(List<AircraftSummary> local, String peerUri) {
        Map<String, AircraftSummary> merged = new LinkedHashMap<>();
        local.forEach(a -> merged.put(a.registrationNumber(), a));
        peerClient.collectList(peerUri, AIRCRAFT_LIST)
                .forEach(dto -> merged.putIfAbsent(dto.getRegistrationNumber(), assembler.toSummary(dto)));
        return List.copyOf(merged.values());
    }

    /** true se o pedido em curso veio de outra réplica (responder só com dados locais). */
    public boolean isPeerRequest() {
        return peerClient.isPeerRequest();
    }

    /** Caminho + query codificados para enviar aos peers; valores null/vazios são omitidos. */
    static String uri(String path, Object... nameValuePairs) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        for (int i = 0; i + 1 < nameValuePairs.length; i += 2) {
            Object value = nameValuePairs[i + 1];
            if (value != null && !value.toString().isBlank()) {
                b.queryParam(nameValuePairs[i].toString(), value);
            }
        }
        return b.encode().build().toUriString();
    }

    static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
