package isep.psoft.aisafe.aircraftmanagement.clients;

import isep.psoft.aisafe.aircraftmanagement.dto.CompatibleRouteDTO;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.remote.ReplicatedServiceClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * ACL para o Flight Routes (pontes B4 e B6 do Integration-Contracts). O JWT do utilizador é
 * reencaminhado automaticamente pela biblioteca common. Sem réplica disponível →
 * RemoteServiceUnavailableException (→ 503).
 */
@Component
public class FlightRoutesClient {

    private static final ParameterizedTypeReference<Map<String, Long>> MINUTES_MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<CompatibleRouteDTO>> ROUTE_LIST = new ParameterizedTypeReference<>() { };

    private final ReplicatedServiceClient flightRoutes;

    public FlightRoutesClient(ReplicatedServiceClient flightRoutesService) {
        this.flightRoutes = flightRoutesService;
    }

    /** Resultado + indicação de que o Flight Routes devolveu dados incompletos (X-Partial-Response). */
    public record MinutesByAircraft(Map<String, Long> minutes, boolean partial) { }

    public record CompatibleRoutes(List<CompatibleRouteDTO> routes, boolean partial) { }

    /** B4b: GET /api/scheduled-flights/statistics/minutes-by-aircraft → {"CS-TUA": 55, ...} */
    public MinutesByAircraft minutesByAircraft() {
        ResponseEntity<Map<String, Long>> response =
                get("/api/scheduled-flights/statistics/minutes-by-aircraft", MINUTES_MAP);
        Map<String, Long> body = response.getBody() == null ? Map.of() : response.getBody();
        return new MinutesByAircraft(body, isPartial(response));
    }

    /** B6: GET /api/routes/compatible?range=&capacity= → rotas ACTIVE compatíveis (todas as réplicas). */
    public CompatibleRoutes compatibleRoutes(double range, int capacity) {
        ResponseEntity<List<CompatibleRouteDTO>> response =
                get("/api/routes/compatible?range={range}&capacity={capacity}", ROUTE_LIST, range, capacity);
        List<CompatibleRouteDTO> body = response.getBody() == null ? List.of() : response.getBody();
        return new CompatibleRoutes(body, isPartial(response));
    }

    private <T> ResponseEntity<T> get(String path, ParameterizedTypeReference<T> type, Object... uriVariables) {
        try {
            return flightRoutes.call((rest, baseUrl) -> rest.get()
                    .uri(baseUrl + path, uriVariables)
                    .retrieve()
                    .toEntity(type));
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.valueOf(status), "Flight Routes rejected the request.");
            }
            throw e;
        }
    }

    private static boolean isPartial(ResponseEntity<?> response) {
        return "true".equalsIgnoreCase(response.getHeaders().getFirst(AisafeHeaders.PARTIAL_RESPONSE));
    }
}
