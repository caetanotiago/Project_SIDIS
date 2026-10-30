package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * Controlo de acessos (RBAC) dos endpoints do Airports, com os papéis do enunciado do PSOFT (WP#2).
 * O resto (filtro JWT, X-Service-Key, auditoria, 401, swagger/h2/health públicos e o
 * anyRequest().authenticated() final) vem da biblioteca common — por isso aqui NÃO se usa anyRequest().
 *
 * <p>A ordem importa: a primeira regra que corresponde ao pedido é a aplicada, por isso os caminhos
 * fixos (/statistics, /grouped) vêm antes de /{iataCode}. Os pedidos entre réplicas levam o JWT do
 * utilizador original, por isso os peers aplicam exatamente as mesmas regras.
 */
@Component
public class AirportsAuthorizationRules implements AuthorizationRules {

    private static final String BACKOFFICE = "BACKOFFICE_OPERATOR";
    private static final String ATCC = "ATCC";

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // US210 — estatísticas dos aeroportos com mais rotas
                .requestMatchers(HttpMethod.GET, "/api/airports/statistics/**").hasRole(BACKOFFICE)
                // US211 — aeroportos agrupados por região ou país
                .requestMatchers(HttpMethod.GET, "/api/airports/grouped").hasRole(ATCC)
                // US108 — pesquisa por cidade, país ou nome
                .requestMatchers(HttpMethod.GET, "/api/airports").hasRole(ATCC)
                // US107 — detalhes pelo IATA. ATCC também por causa do Flight Routes (contrato B2:
                // US110, US209, US212 e US216 são feitos por um ATCC e reencaminham o seu JWT)
                .requestMatchers(HttpMethod.GET, "/api/airports/*").hasAnyRole(BACKOFFICE, ATCC)

                // US106 / US207 — registar aeroporto
                .requestMatchers(HttpMethod.POST, "/api/airports").hasRole(BACKOFFICE)
                // US106a — certificar um modelo de aeronave no aeroporto
                .requestMatchers(HttpMethod.POST, "/api/airports/*/certifications").hasAnyRole(BACKOFFICE, ATCC)
                // US109 — alterar o estado operacional
                .requestMatchers(HttpMethod.PATCH, "/api/airports/*/status").hasRole(BACKOFFICE)
                // US208 — alterar horário e contactos
                .requestMatchers(HttpMethod.PATCH, "/api/airports/*").hasRole(BACKOFFICE)

                // Qualquer outro pedido a /api/airports (método ou caminho não previstos) é recusado
                .requestMatchers("/api/airports", "/api/airports/**").denyAll();
    }
}
