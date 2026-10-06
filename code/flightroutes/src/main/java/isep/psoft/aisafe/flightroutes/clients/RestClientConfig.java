package isep.psoft.aisafe.flightroutes.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Clientes dos microsserviços remotos. Cada um conhece TODAS as réplicas do serviço
 * (services.*.urls) e faz load balancing round-robin com failover, timeouts, retry e circuit
 * breaker (biblioteca common). Cada pedido leva o JWT do utilizador, o correlation id e a
 * X-Service-Key, para o serviço remoto aplicar o controlo de acessos com o mesmo utilizador/roles.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public ReplicatedServiceClient aircraftService(ReplicatedServiceClientFactory factory,
                                                   @Value("${services.aircraft.urls}") List<String> urls) {
        return factory.create("Aircraft Management", urls);
    }

    @Bean
    public ReplicatedServiceClient airportsService(ReplicatedServiceClientFactory factory,
                                                   @Value("${services.airports.urls}") List<String> urls) {
        return factory.create("Airports", urls);
    }
}
