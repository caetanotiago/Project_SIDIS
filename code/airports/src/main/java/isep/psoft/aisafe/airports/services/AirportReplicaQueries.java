package isep.psoft.aisafe.airports.services;

import isep.psoft.aisafe.airports.assemblers.AirportAssembler;
import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import isep.sidis.common.peers.PeerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas de aeroportos sobre TODAS as réplicas do Airports (peer-to-peer query resolution).
 *
 * <p>Cada réplica guarda só os aeroportos registados nela. Para responder a um GET:
 * <ol>
 *   <li>lê os dados locais;</li>
 *   <li>pede o MESMO endpoint às réplicas peer, em paralelo (via {@link PeerClient}); estas
 *       respondem só com os seus dados locais (header X-Peer-Hops), por isso não há ciclos;</li>
 *   <li>junta os resultados, eliminando duplicados pelo código IATA (a cópia local tem prioridade).</li>
 * </ol>
 * Se um peer não responder, o resultado é o que se conseguiu juntar e a resposta HTTP leva
 * {@code X-Partial-Response: true} (disponibilidade em vez de erro — AP).
 */
@Component
@RequiredArgsConstructor
public class AirportReplicaQueries {

    static final ParameterizedTypeReference<List<AirportDTO>> AIRPORT_LIST = new ParameterizedTypeReference<>() { };

    private final AirportRepository airportRepository;
    private final PeerClient peerClient;
    private final AirportAssembler assembler;

    /** Aeroporto pelo IATA: local, senão a primeira réplica peer que o tiver. */
    public Optional<AirportDTO> findByIata(String iata) {
        Optional<AirportDTO> local = airportRepository.findById(iata).map(assembler::toDTO);
        if (local.isPresent()) {
            return local;
        }
        return peerClient.findFirst("/api/airports/" + pathSegment(iata), AirportDTO.class);
    }

    /** Junta os aeroportos locais com os devolvidos pelos peers para o mesmo endpoint, ordenados por IATA. */
    public List<AirportDTO> withPeers(List<Airport> local, String peerUri) {
        Map<String, AirportDTO> merged = new LinkedHashMap<>();
        local.forEach(a -> merged.put(a.getIataCode(), assembler.toDTO(a)));
        peerClient.collectList(peerUri, AIRPORT_LIST)
                .forEach(dto -> merged.putIfAbsent(dto.iataCode(), dto));
        return merged.values().stream()
                .sorted(Comparator.comparing(AirportDTO::iataCode))
                .toList();
    }

    public boolean isPeerRequest() {
        return peerClient.isPeerRequest();
    }

    /** Caminho + query codificados, para enviar aos peers. Pares nome/valor; valores null são omitidos. */
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
