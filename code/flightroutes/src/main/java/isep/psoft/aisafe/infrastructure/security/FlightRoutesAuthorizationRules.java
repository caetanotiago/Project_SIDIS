package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

// Só as regras dos endpoints deste microsserviço (WP#3A / WP#3B). O resto da configuração
// (filtro JWT, X-Service-Key, auditoria, stateless, 401, swagger/h2/health públicos) vem da biblioteca common.
// Os pedidos peer-to-peer reencaminham o JWT do utilizador e usam o MESMO endpoint, por isso
// as réplicas aplicam exatamente as mesmas regras que a réplica que recebeu o pedido.
@Component
public class FlightRoutesAuthorizationRules implements AuthorizationRules {

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // Consultas pedidas pelos outros microsserviços (o JWT do utilizador é reencaminhado,
                // por isso os roles são os do endpoint original em cada serviço).
                // US203 — GET /api/aircrafts/{reg}/compatible-routes (Aircraft Management)
                .requestMatchers(HttpMethod.GET,   "/api/routes/compatible").hasAnyRole("ATCC", "MAINTENANCE_SUPERVISOR", "MAINTENANCE_TECHNICIAN")
                // US210 — GET /airports/statistics/busiest (Airports)
                .requestMatchers(HttpMethod.GET,   "/api/routes/statistics/count-by-airport").hasRole("BACKOFFICE_OPERATOR")

                //Regras para o WP#3A (FlightRoutes)
                .requestMatchers(HttpMethod.POST,  "/api/routes").hasRole("ATCC")
                .requestMatchers(HttpMethod.PATCH, "/api/routes/*").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR")
                .requestMatchers(HttpMethod.GET,   "/api/routes/**").hasRole("ATCC")

                //Regras para o WP#3B (Scheduled Flights / Flight Operations); inclui US206 (operational-hours),
                // a pesquisa /search e as estatísticas /statistics/** usadas na agregação entre réplicas
                .requestMatchers(HttpMethod.POST,  "/api/scheduled-flights").hasRole("ATCC")
                .requestMatchers(HttpMethod.GET,   "/api/scheduled-flights/**").hasRole("ATCC");
    }
}
