package isep.sidis.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Regras de acesso específicas de cada microsserviço. Cada serviço declara um bean
 * que implementa esta interface; a SecurityFilterChain comum aplica-as antes do
 * "anyRequest().authenticated()" final.
 */
@FunctionalInterface
public interface AuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth);
}
