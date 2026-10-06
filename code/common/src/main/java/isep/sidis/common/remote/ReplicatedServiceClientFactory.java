package isep.sidis.common.remote;

import isep.sidis.common.resilience.ResilientExecutor;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Bean partilhado para criar clientes de outros microsserviços com a mesma configuração
 * (timeouts, TLS, headers propagados, retry e circuit breaker).
 *
 * <pre>
 * &#64;Bean
 * ReplicatedServiceClient airportsService(ReplicatedServiceClientFactory f,
 *                                         &#64;Value("${services.airports.urls}") List&lt;String&gt; urls) {
 *     return f.create("Airports", urls);
 * }
 * </pre>
 */
public class ReplicatedServiceClientFactory {

    private final RestClient restClient;
    private final ResilientExecutor executor;

    public ReplicatedServiceClientFactory(RestClient restClient, ResilientExecutor executor) {
        this.restClient = restClient;
        this.executor = executor;
    }

    public ReplicatedServiceClient create(String serviceName, List<String> baseUrls) {
        return new ReplicatedServiceClient(serviceName, baseUrls, restClient, executor);
    }
}
