package isep.psoft.aisafe.airports.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Cliente HTTP do microsserviço Flight Routes (anti-corruption layer). Contrato B5:
 * {@code GET /api/routes/statistics/count-by-airport} devolve {@code {"OPO": 2, "LIS": 1}} — o número
 * de rotas de cada aeroporto (origem + destino), já somado sobre todas as réplicas do Flight Routes.
 * Aeroportos sem rotas não aparecem (contam como 0).
 *
 * <p>Erros: 401/403 → o mesmo estado (o Flight Routes recusou o utilizador); nenhuma réplica do
 * Flight Routes responde → RemoteServiceUnavailableException (503), lançada pelo ReplicatedServiceClient.
 */
@Component
public class FlightRoutesClient {

    private static final String SERVICE = "Flight Routes";

    private static final ParameterizedTypeReference<Map<String, Long>> COUNTS = new ParameterizedTypeReference<>() { };

    private final ReplicatedServiceClient flightRoutesService;

    public FlightRoutesClient(@Qualifier("flightRoutesService") ReplicatedServiceClient flightRoutesService) {
        this.flightRoutesService = flightRoutesService;
    }

    public Map<String, Long> routeCountsByAirport() {
        Map<String, Long> counts = flightRoutesService.call((rest, baseUrl) -> rest.get()
                .uri(baseUrl + "/api/routes/statistics/count-by-airport")
                .retrieve()
                .onStatus(s -> s.value() == 401 || s.value() == 403, (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.valueOf(res.getStatusCode().value()),
                            SERVICE + " rejected the request.");
                })
                .body(COUNTS));
        return counts == null ? Map.of() : counts;
    }
}
