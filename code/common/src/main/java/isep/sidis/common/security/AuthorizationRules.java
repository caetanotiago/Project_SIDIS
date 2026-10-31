package isep.sidis.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Regras de acesso específicas de cada microsserviço. Cada serviço declara um bean
 * que implementa esta interface; a SecurityFilterChain comum aplica-as antes do
 * "anyRequest().authenticated()" final.
 *
 * <p><b>As regras NÃO podem chamar {@code anyRequest()}: a biblioteca common acrescenta-o.</b>
 * Declarem só os endpoints do próprio serviço ({@code requestMatchers(...)}), do mais específico para o
 * mais geral; o que não for declarado fica a exigir autenticação. Para recusar o resto de um prefixo
 * usem {@code requestMatchers("/api/x/**").denyAll()}. Se uma implementação chamar {@code anyRequest()},
 * o serviço não arranca e a mensagem indica a classe.
 */
@FunctionalInterface
public interface AuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth);
}
