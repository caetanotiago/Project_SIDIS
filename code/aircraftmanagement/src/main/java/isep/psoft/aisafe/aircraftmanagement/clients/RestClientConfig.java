package isep.psoft.aisafe.aircraftmanagement.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Clientes dos outros microsserviços (todas as réplicas: round-robin + failover + circuit breaker). */
@Configuration
public class RestClientConfig {

    @Bean
    ReplicatedServiceClient flightRoutesService(ReplicatedServiceClientFactory factory,
                                                @Value("${services.flightroutes.urls}") List<String> urls) {
        return factory.create("Flight Routes", urls);
    }
}
