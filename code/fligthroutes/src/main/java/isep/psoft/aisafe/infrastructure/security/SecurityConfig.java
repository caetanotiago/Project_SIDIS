package isep.psoft.aisafe.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Só as regras dos endpoints deste microsserviço (WP#3A / WP#3B); as restantes ficam nos outros serviços.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error permitido: senão um 403 (role errado) é convertido em 401 no error dispatch
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()

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

                        //Regras para o WP#3B (Scheduled Flights / Flight Operations); inclui US206 (operational-hours)
                        .requestMatchers(HttpMethod.POST,  "/api/scheduled-flights").hasRole("ATCC")
                        .requestMatchers(HttpMethod.GET,   "/api/scheduled-flights/**").hasRole("ATCC")

                        .anyRequest().authenticated()
                )
                // Return 401 (not 403) when an unauthenticated request hits a protected endpoint.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.frameOptions(f -> f.disable()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
