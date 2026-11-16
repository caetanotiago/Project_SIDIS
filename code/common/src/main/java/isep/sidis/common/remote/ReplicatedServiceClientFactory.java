package isep.sidis.common.remote;

import isep.sidis.common.resilience.ResilientExecutor;
import org.springframework.web.client.RestClient;

import java.util.List;

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
