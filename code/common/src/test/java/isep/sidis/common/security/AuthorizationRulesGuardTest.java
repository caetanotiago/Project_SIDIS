package isep.sidis.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As AuthorizationRules de um serviço não podem chamar anyRequest() (a common acrescenta-o):
 * o arranque tem de falhar com uma mensagem que diga qual é a classe.
 */
class AuthorizationRulesGuardTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtSecurityAutoConfiguration.class))
            .withPropertyValues("jwt.secret=test-secret-with-at-least-thirty-two-bytes-1234567890");

    static class WellBehavedRules implements AuthorizationRules {
        @Override
        public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
            auth.requestMatchers("/api/things/**").hasRole("ATCC");
        }
    }

    static class AnyRequestRules implements AuthorizationRules {
        @Override
        public void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
            auth.anyRequest().authenticated();
        }
    }

    @Test
    void rulesWithoutAnyRequestStartNormally() {
        runner.withBean(WellBehavedRules.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SecurityFilterChain.class));
    }

    @Test
    void rulesThatCallAnyRequestFailWithTheClassName() {
        runner.withBean(AnyRequestRules.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause().hasMessageContaining("anyRequest");
            assertThat(messages(context.getStartupFailure()))
                    .contains(AnyRequestRules.class.getName() + " must not call anyRequest()");
        });
    }

    @Test
    void laterRulesAreStillAppliedAfterAWellBehavedOne() {
        runner.withBean("a", WellBehavedRules.class).withBean("b", WellBehavedRules.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
