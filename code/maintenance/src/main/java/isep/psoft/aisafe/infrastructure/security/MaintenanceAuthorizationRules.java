package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

// Regras de acesso dos endpoints deste microsserviço (WP#4A/WP#4B). O resto (filtro JWT, X-Service-Key,
// auditoria, 401, swagger/h2/health públicos) vem da biblioteca common.
// Sem regras aqui, qualquer pedido autenticado é aceite (anyRequest().authenticated()).
@Component
public class MaintenanceAuthorizationRules implements AuthorizationRules {

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // TODO: regras por endpoint/role. Exemplo (ver FlightRoutesAuthorizationRules):
        // auth.requestMatchers(HttpMethod.POST, "/api/...").hasRole("BACKOFFICE_OPERATOR")
        //     .requestMatchers(HttpMethod.GET,  "/api/...").hasAnyRole("ATCC", "BACKOFFICE_OPERATOR");
    }
}
