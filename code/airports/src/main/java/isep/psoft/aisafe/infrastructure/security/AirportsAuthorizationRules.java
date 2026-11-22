package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

// Permissões por endpoint
@Component
public class AirportsAuthorizationRules implements AuthorizationRules {
    private static final String BACKOFFICE = "BACKOFFICE_OPERATOR";
    private static final String ATCC = "ATCC";

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                .requestMatchers(HttpMethod.GET, "/api/airports/statistics/**").hasRole(BACKOFFICE)
                .requestMatchers(HttpMethod.GET, "/api/airports/grouped").hasRole(ATCC)
                .requestMatchers(HttpMethod.GET, "/api/airports").hasRole(ATCC)
                .requestMatchers(HttpMethod.GET, "/api/airports/*").hasAnyRole(BACKOFFICE, ATCC)

                .requestMatchers(HttpMethod.POST, "/api/airports").hasRole(BACKOFFICE)
                .requestMatchers(HttpMethod.POST, "/api/airports/*/certifications").hasAnyRole(BACKOFFICE, ATCC)
                .requestMatchers(HttpMethod.PATCH, "/api/airports/*/status").hasRole(BACKOFFICE)
                .requestMatchers(HttpMethod.PATCH, "/api/airports/*").hasRole(BACKOFFICE)

                .requestMatchers("/api/airports", "/api/airports/**").denyAll();
    }
}
