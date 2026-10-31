package isep.psoft.aisafe.maintenance.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Cliente resiliente (round-robin + failover) para as 3 réplicas do Aircraft Management. */
@Configuration
public class RestClientConfig {

    @Bean
    public ReplicatedServiceClient aircraftService(ReplicatedServiceClientFactory factory,
                                                   @Value("${services.aircraft.urls}") List<String> urls) {
        return factory.create("Aircraft Management", urls);
    }
}
