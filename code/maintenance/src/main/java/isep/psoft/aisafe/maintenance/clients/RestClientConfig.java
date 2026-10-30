package isep.psoft.aisafe.maintenance.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures resilient HTTP clients for remote replicated services.
 *
 * @author Diogo Fernandes - 1240632
 */
@Configuration
public class RestClientConfig {

    @Bean
    public ReplicatedServiceClient aircraftService(
            ReplicatedServiceClientFactory factory,
            @Value("${services.aircraft.urls}") List<String> urls) {
        return factory.create("Aircraft Management", urls);
    }
}
