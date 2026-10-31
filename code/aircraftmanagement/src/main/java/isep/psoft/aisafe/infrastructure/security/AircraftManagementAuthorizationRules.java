package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

// Regras do WP#1A/WP#1B. O JWT do utilizador é reencaminhado nas chamadas entre serviços e entre
// réplicas, por isso estas regras têm de aceitar os roles dos utilizadores dos serviços consumidores
// (Integration-Contracts: B1 → ATCC; B3 → MAINTENANCE_*). As regras mais específicas vêm primeiro.
@Component
public class AircraftManagementAuthorizationRules implements AuthorizationRules {

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // US101 – registar modelo
                .requestMatchers(HttpMethod.POST, "/api/aircraft-models").hasRole("BACKOFFICE_OPERATOR")
                // US204 – Top modelos (Backoffice pela US; ATCC porque é o único role que o Flight Routes
                // aceita em /api/scheduled-flights/** – ver Q4 do contrato)
                .requestMatchers(HttpMethod.GET, "/api/aircraft-models/top-utilized")
                        .hasAnyRole("BACKOFFICE_OPERATOR", "ATCC")
                // Ponte B1b (Flight Routes, US212) e consulta de modelos
                .requestMatchers(HttpMethod.GET, "/api/aircraft-models/**")
                        .hasAnyRole("ATCC", "BACKOFFICE_OPERATOR", "MAINTENANCE_TECHNICIAN", "MAINTENANCE_SUPERVISOR")

                // US102 – registar aeronave
                .requestMatchers(HttpMethod.POST, "/api/aircrafts").hasRole("ATCC")
                // US105 / ponte B3b – alterar estado (ATCC ou Maintenance)
                .requestMatchers(HttpMethod.PATCH, "/api/aircrafts/*/status")
                        .hasAnyRole("ATCC", "MAINTENANCE_TECHNICIAN", "MAINTENANCE_SUPERVISOR")
                // US206 e US203 – só ATCC
                .requestMatchers(HttpMethod.GET, "/api/aircrafts/operational-hours").hasRole("ATCC")
                .requestMatchers(HttpMethod.GET, "/api/aircrafts/*/compatible-routes").hasRole("ATCC")
                // US103, US104 / pontes B1a e B3a
                .requestMatchers(HttpMethod.GET, "/api/aircrafts/**")
                        .hasAnyRole("ATCC", "BACKOFFICE_OPERATOR", "MAINTENANCE_TECHNICIAN", "MAINTENANCE_SUPERVISOR");
    }
}
