package isep.psoft.aisafe.flightroutes.services;

import isep.psoft.aisafe.flightroutes.assemblers.FlightRouteAssembler;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.dto.FlightRouteDTO;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
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
 * Consultas de rotas sobre TODAS as réplicas do Flight Routes (peer-to-peer query resolution).
 *
 * <p>Cada réplica guarda só as rotas que foram criadas nela. Para responder a um GET:
 * <ol>
 *   <li>lê os dados locais;</li>
 *   <li>pede o MESMO endpoint às réplicas peer (em paralelo, via {@link PeerClient}) — as réplicas
 *       peer respondem só com os seus dados locais;</li>
 *   <li>junta os resultados, eliminando duplicados pelo id (a cópia local tem prioridade).</li>
 * </ol>
 * Quando o pedido veio de um peer, o PeerClient não volta a encaminhar: o resultado é só o local.
 */
@Component
@RequiredArgsConstructor
public class RouteReplicaQueries {

    static final ParameterizedTypeReference<List<FlightRouteDTO>> ROUTE_LIST = new ParameterizedTypeReference<>() { };

    private final FlightRouteRepository routeRepository;
    private final PeerClient peerClient;
    private final FlightRouteAssembler assembler;

    /** Rota pelo id: local, senão a primeira réplica peer que a tiver. */
    public Optional<RouteSummary> findById(String id) {
        Optional<RouteSummary> local = routeRepository.findById(id).map(FlightRoute::toSummary);
        if (local.isPresent()) {
            return local;
        }
        return peerClient.findFirst("/api/routes/" + pathSegment(id), FlightRouteDTO.class)
                .map(assembler::toSummary);
    }

    /** Junta as rotas locais com as devolvidas pelos peers para o mesmo endpoint. */
    public List<RouteSummary> withPeers(List<FlightRoute> local, String peerUri) {
        Map<String, RouteSummary> merged = new LinkedHashMap<>();
        local.forEach(r -> merged.put(r.getId(), r.toSummary()));
        peerClient.collectList(peerUri, ROUTE_LIST).forEach(dto ->
                merged.putIfAbsent(dto.getId(), assembler.toSummary(dto)));
        return List.copyOf(merged.values());
    }

    /** A "Network": todas as rotas ativas de todas as réplicas (US214, US215, US216). */
    public List<RouteSummary> activeNetwork() {
        return withPeers(routeRepository.findAllActive(), uri("/api/routes/active", "sortBy", "distance"));
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
