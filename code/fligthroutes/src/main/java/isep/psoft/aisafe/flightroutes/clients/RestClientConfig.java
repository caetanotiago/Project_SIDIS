package isep.psoft.aisafe.flightroutes.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RestClients para os microsserviços remotos. Cada pedido reencaminha o header
 * Authorization do pedido original, para que o serviço remoto aplique o controlo
 * de acessos com o mesmo utilizador/roles (JWT propagation).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient aircraftRestClient(RestClient.Builder builder,
                                         @Value("${services.aircraft.url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(propagateJwt())
                .build();
    }

    @Bean
    public RestClient airportsRestClient(RestClient.Builder builder,
                                         @Value("${services.airports.url}") String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(propagateJwt())
                .build();
    }

    private static ClientHttpRequestInterceptor propagateJwt() {
        return (request, body, execution) -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, auth);
                }
            }
            return execution.execute(request, body);
        };
    }
}
