package isep.psoft.aisafe.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-only security configuration for isolated Postman testing.
 *
 * @author Diogo Fernandes - 1240632
 */
@Configuration
@Profile("maintenance-local-test")
public class MaintenanceLocalTestSecurityConfiguration {

    @Bean
    public SecurityFilterChain maintenanceLocalTestSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/maintenance/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
