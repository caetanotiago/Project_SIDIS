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
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.ClassUtils;

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

    @Bean
    @ConditionalOnProperty(prefix = "aisafe.security.dev-token", name = "enabled", havingValue = "true")
    public DevTokenController devTokenController(JwtTokenProvider jwtTokenProvider) {
        return new DevTokenController(jwtTokenProvider);
    }

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
                    auth.requestMatchers("/h2-console/**", "/error").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/auth/dev-token").permitAll();

                    serviceRules.orderedStream().forEach(rules -> applyServiceRules(auth, rules));

                    auth.anyRequest().authenticated();
                })

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.frameOptions(f -> f.disable()))
                .addFilterAfter(new AuditLogFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(new ServiceKeyFilter(props.getSecurity().getServiceKey(),
                                props.getSecurity().getServiceOnlyPaths()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static final RequestMatcher NEVER = request -> false;

    static void applyServiceRules(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
                                  AuthorizationRules rules) {
        String name = ClassUtils.getUserClass(rules).getName();
        try {
            rules.configure(auth);

            auth.requestMatchers(NEVER).denyAll();
        } catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains("anyRequest")) {
                throw e;
            }
            throw new IllegalStateException(name + " must not call anyRequest(): the common library adds "
                    + "anyRequest().authenticated() after every service's rules. Remove that call from " + name
                    + " (list only the service's own endpoints).", e);
        }
    }
}
