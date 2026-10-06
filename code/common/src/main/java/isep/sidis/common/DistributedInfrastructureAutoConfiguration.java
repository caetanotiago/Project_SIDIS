package isep.sidis.common;

import isep.sidis.common.config.AisafeProperties;
import isep.sidis.common.http.HttpClientFactory;
import isep.sidis.common.http.InterServiceHeadersInterceptor;
import isep.sidis.common.http.OutboundHeaders;
import isep.sidis.common.observability.CorrelationIdFilter;
import isep.sidis.common.peers.PeerClient;
import isep.sidis.common.peers.PeersHealthIndicator;
import isep.sidis.common.remote.ReplicatedServiceClientFactory;
import isep.sidis.common.resilience.CircuitBreakerRegistry;
import isep.sidis.common.resilience.ResilientExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Infraestrutura distribuída partilhada (auto-configurada em todos os microsserviços):
 * <ul>
 *   <li>{@link PeerClient} — encaminhamento/agregação de pedidos entre réplicas do mesmo serviço;</li>
 *   <li>{@link ReplicatedServiceClientFactory} — clientes de outros serviços com load balancing e failover;</li>
 *   <li>{@link ResilientExecutor} — retry com exponential backoff + circuit breaker;</li>
 *   <li>RestClient com timeouts, TLS opcional e headers propagados (JWT, correlation id, service key);</li>
 *   <li>{@link CorrelationIdFilter} e health indicator dos peers.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AisafeProperties.class)
public class DistributedInfrastructureAutoConfiguration {

    public static final String INTER_SERVICE_REST_CLIENT = "interServiceRestClient";

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(AisafeProperties props) {
        return new CircuitBreakerRegistry(props.getCircuitBreaker().getFailureThreshold(),
                props.getCircuitBreaker().getOpenDuration(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilientExecutor resilientExecutor(CircuitBreakerRegistry registry, AisafeProperties props) {
        return new ResilientExecutor(registry, props.getHttp().getMaxRetries(), props.getHttp().getInitialBackoff());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundHeaders outboundHeaders(AisafeProperties props, Environment env) {
        return new OutboundHeaders(props.getSecurity().getServiceKey(), instanceId(props, env));
    }

    /** RestClient para chamadas entre serviços: timeouts, TLS e headers propagados. */
    @Bean(name = INTER_SERVICE_REST_CLIENT)
    @ConditionalOnMissingBean(name = INTER_SERVICE_REST_CLIENT)
    public RestClient interServiceRestClient(ObjectProvider<RestClient.Builder> builder,
                                             OutboundHeaders outboundHeaders,
                                             AisafeProperties props,
                                             ResourceLoader resourceLoader) {
        // Usa o builder do Boot (mesmo Jackson da aplicação) quando existe
        RestClient.Builder b = builder.getIfAvailable(RestClient::builder).clone();
        return b.requestFactory(HttpClientFactory.create(props, resourceLoader))
                .requestInterceptor(new InterServiceHeadersInterceptor(outboundHeaders))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReplicatedServiceClientFactory replicatedServiceClientFactory(
            @Qualifier(INTER_SERVICE_REST_CLIENT) RestClient restClient, ResilientExecutor executor) {
        return new ReplicatedServiceClientFactory(restClient, executor);
    }

    @Bean
    @ConditionalOnMissingBean
    public PeerClient peerClient(@Qualifier(INTER_SERVICE_REST_CLIENT) RestClient restClient,
                                 ResilientExecutor executor, OutboundHeaders outboundHeaders,
                                 AisafeProperties props) {
        return new PeerClient(props.getPeers().getUrls(), restClient, executor, outboundHeaders,
                props.getHttp().getAggregationTimeout());
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(AisafeProperties props, Environment env) {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter(instanceId(props, env)));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // antes da segurança: tudo fica com correlation id
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthConfiguration {

        @Bean(name = "peers")
        public PeersHealthIndicator peersHealthIndicator(PeerClient peerClient, CircuitBreakerRegistry registry) {
            return new PeersHealthIndicator(peerClient, registry);
        }
    }

    /** {@code aisafe.instance-id}, ou "nome-da-aplicação:porta" se não estiver definido. */
    static String instanceId(AisafeProperties props, Environment env) {
        if (props.getInstanceId() != null && !"unknown".equals(props.getInstanceId())) {
            return props.getInstanceId();
        }
        return env.getProperty("spring.application.name", "service") + ":" + env.getProperty("server.port", "8080");
    }
}
