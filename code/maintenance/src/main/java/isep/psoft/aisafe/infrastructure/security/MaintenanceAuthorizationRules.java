package isep.psoft.aisafe.infrastructure.security;

import isep.sidis.common.security.AuthorizationRules;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceAuthorizationRules implements AuthorizationRules {

    private static final String TECH = "MAINTENANCE_TECHNICIAN";
    private static final String SUP = "MAINTENANCE_SUPERVISOR";
    private static final String ATCC = "ATCC";

    @Override
    public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth

                .requestMatchers(HttpMethod.POST, "/api/maintenance-templates").hasRole(TECH)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-templates", "/api/maintenance-templates/**")
                        .hasAnyRole(TECH, SUP, ATCC)

                .requestMatchers(HttpMethod.POST, "/api/maintenance-records").hasRole(TECH)
                .requestMatchers(HttpMethod.PATCH, "/api/maintenance-records/*/complete",
                        "/api/maintenance-records/*/component").hasRole(TECH)

                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/statistics/**",
                        "/api/maintenance-records/reports/costs").hasRole(ATCC)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/reports/turnaround",
                        "/api/maintenance-records/ongoing").hasRole(SUP)

                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/aircraft/**").hasAnyRole(TECH, SUP)
                .requestMatchers(HttpMethod.GET, "/api/maintenance-records").hasAnyRole(TECH, SUP)

                .requestMatchers(HttpMethod.GET, "/api/maintenance-records/search", "/api/maintenance-records/*")
                        .hasAnyRole(TECH, SUP, ATCC)

                .requestMatchers(HttpMethod.GET, "/api/maintenance-alerts").hasRole(ATCC);
    }
}
