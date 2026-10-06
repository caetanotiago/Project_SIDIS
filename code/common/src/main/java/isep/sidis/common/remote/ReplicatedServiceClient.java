package isep.sidis.common.remote;

import isep.sidis.common.resilience.ResilientExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Cliente de OUTRO microsserviço que tem várias réplicas (ex.: Airports em 8083/8183/8283).
 *
 * <p>Load balancing round-robin: cada chamada começa numa réplica diferente. Se essa réplica tiver
 * uma falha transitória (rede, 5xx, circuito aberto) tenta a seguinte (failover), cada uma com
 * retry + circuit breaker ({@link ResilientExecutor}). Respostas 4xx são devolvidas ao chamador
 * sem failover, porque a réplica respondeu. Se nenhuma réplica responder lança
 * {@link RemoteServiceUnavailableException} (→ 503).
 *
 * <pre>
 * AirportResponse a = airports.call((rest, baseUrl) -> rest.get()
 *         .uri(baseUrl + "/airports/{iata}", iata)
 *         .retrieve()
 *         .body(AirportResponse.class));
 * </pre>
 */
public class ReplicatedServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ReplicatedServiceClient.class);

    private final String serviceName;
    private final List<String> baseUrls;
    private final RestClient restClient;
    private final ResilientExecutor executor;
    private final AtomicInteger next = new AtomicInteger();

    public ReplicatedServiceClient(String serviceName, List<String> baseUrls,
                                   RestClient restClient, ResilientExecutor executor) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one URL is required for " + serviceName);
        }
        this.serviceName = serviceName;
        this.baseUrls = baseUrls.stream().map(ReplicatedServiceClient::trimSlash).toList();
        this.restClient = restClient;
        this.executor = executor;
    }

    public <T> T call(BiFunction<RestClient, String, T> request) {
        int start = Math.floorMod(next.getAndIncrement(), baseUrls.size());
        RuntimeException lastFailure = null;

        for (int i = 0; i < baseUrls.size(); i++) {
            String baseUrl = baseUrls.get((start + i) % baseUrls.size());
            try {
                return executor.execute(baseUrl, () -> request.apply(restClient, baseUrl));
            } catch (RuntimeException e) {
                if (!ResilientExecutor.isTransient(e)) {
                    throw e;
                }
                log.warn("{} replica {} unavailable ({}), trying next replica",
                        serviceName, baseUrl, e.getMessage());
                lastFailure = e;
            }
        }
        throw new RemoteServiceUnavailableException(serviceName, lastFailure);
    }

    public String getServiceName() {
        return serviceName;
    }

    public List<String> getBaseUrls() {
        return baseUrls;
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
