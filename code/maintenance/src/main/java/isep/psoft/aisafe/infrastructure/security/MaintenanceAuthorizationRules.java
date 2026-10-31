package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * Regras de acesso do WP#4 (as mais específicas primeiro). O resto (filtro JWT, X-Service-Key,
 * auditoria, 401, swagger/health públicos e o anyRequest().authenticated() final) vem da common.
 *
 * <p>Os pedidos entre réplicas levam o JWT do utilizador, por isso os endpoints usados pelos peers
 * (/search, /statistics/**, GET de templates) aceitam também os roles dos casos de uso que os chamam.
 */
@Component
public class MaintenanceAuthorizationRules implements AuthorizationRules {

    private static final String TECH = "MAINTENANCE_TECHNICIAN";
    private static final String SUP = "MAINTENANCE_SUPERVISOR";
    private static final String ATCC = "ATCC";

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // US115 (templates); GET também usado pelos peers na US115 (registos) e na US222
                .requestMatchers(HttpMethod.POST, "/api/maintenance-templates").hasRole(TECH)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-templates", "/api/maintenance-templates/**")
                        .hasAnyRole(TECH, SUP, ATCC)

                // US115 (registos), US119, US217
                .requestMatchers(HttpMethod.POST, "/api/maintenance-records").hasRole(TECH)
                .requestMatchers(HttpMethod.PATCH, "/api/maintenance-records/*/complete",
                        "/api/maintenance-records/*/component").hasRole(TECH)

                // US117, US220 e resumo da US222 (ATCC); US221 e US219 (supervisor)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/statistics/**",
                        "/api/maintenance-records/reports/costs").hasRole(ATCC)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/reports/turnaround",
                        "/api/maintenance-records/ongoing").hasRole(SUP)

                // US116, US218
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/aircraft/**").hasAnyRole(TECH, SUP)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records").hasAnyRole(TECH, SUP)

                // Pesquisa usada pelos peers (US116/US218 técnico/supervisor, US219 supervisor, US119 técnico)
                // e consulta por id
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/search", "/api/maintenance-records/*")
                        .hasAnyRole(TECH, SUP, ATCC)

                // US222
                .requestMatchers(HttpMethod.GET, "/api/maintenance-alerts").hasRole(ATCC);
    }
}
