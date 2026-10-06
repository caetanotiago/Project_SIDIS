package isep.sidis.common.security;

import isep.sidis.common.config.AisafeProperties;
import isep.sidis.common.observability.AuditLogFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Auto-configuração da segurança partilhada. Registada em
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports,
 * por isso qualquer microsserviço que tenha o common no classpath recebe-a sem
 * @ComponentScan nem @Import, independentemente do package da sua classe main.
 *
 * <p>Filtros, por ordem: auditoria → X-Service-Key (inter-service authentication) → JWT
 * (utilizador e roles) → regras de autorização de cada serviço ({@link AuthorizationRules}).
 *
 * <p>Corre antes da ServletWebSecurityAutoConfiguration do Boot, para que a
 * SecurityFilterChain por omissão (form login + password gerada) não seja criada.
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AisafeProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class JwtSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider(@Value("${jwt.secret}") String secret,
                                             @Value("${jwt.expiration:86400000}") long expirationMs) {
        return new JwtTokenProvider(secret, expirationMs);
    }

    // Só em desenvolvimento: emite JWTs para Postman/Swagger enquanto não há serviço de login.
    @Bean
    @ConditionalOnProperty(prefix = "aisafe.security.dev-token", name = "enabled", havingValue = "true")
    public DevTokenController devTokenController(JwtTokenProvider jwtTokenProvider) {
        return new DevTokenController(jwtTokenProvider);
    }

    // Um serviço pode substituir a chain inteira declarando a sua própria SecurityFilterChain.
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http,
                                                      JwtTokenProvider jwtTokenProvider,
                                                      AisafeProperties props,
                                                      ObjectProvider<AuthorizationRules> serviceRules) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // /error permitido: senão um 403 (role errado) é convertido em 401 no error dispatch
                    auth.requestMatchers("/h2-console/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Health checks (load balancer / monitorização) e token de desenvolvimento
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/auth/dev-token").permitAll();

                    // Regras de cada microsserviço (beans AuthorizationRules)
                    serviceRules.orderedStream().forEach(rules -> rules.configure(auth));

                    auth.anyRequest().authenticated();
                })
                // Return 401 (not 403) when an unauthenticated request hits a protected endpoint.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.frameOptions(f -> f.disable()))
                .addFilterAfter(new AuditLogFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(new ServiceKeyFilter(props.getSecurity().getServiceKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
