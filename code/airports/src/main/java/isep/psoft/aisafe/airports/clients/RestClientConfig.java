package isep.psoft.aisafe.airports.clients;

import isep.sidis.common.remote.ReplicatedServiceClient;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean
    public ReplicatedServiceClient flightRoutesService(ReplicatedServiceClientFactory factory,
                                                       @Value("${services.flightroutes.urls}") List<String> urls) {
        return factory.create("Flight Routes", urls);
    }
}
