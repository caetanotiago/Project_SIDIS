package isep.sidis.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuração distribuída partilhada por todos os microsserviços (prefixo {@code aisafe}).
 * Os valores por omissão servem para desenvolvimento local; cada instância define pelo menos
 * {@code aisafe.peers.urls} e {@code aisafe.security.service-key}.
 *
 * <pre>
 * aisafe.instance-id=flightroutes-1
 * aisafe.peers.urls=http://localhost:8184,http://localhost:8284
 * aisafe.http.connect-timeout=1s
 * aisafe.http.read-timeout=3s
 * aisafe.http.max-retries=2
 * aisafe.http.initial-backoff=100ms
 * aisafe.http.aggregation-timeout=5s
 * aisafe.circuit-breaker.failure-threshold=3
 * aisafe.circuit-breaker.open-duration=10s
 * aisafe.security.service-key=...
 * aisafe.security.dev-token.enabled=true
 * aisafe.tls.trust-store=classpath:tls/aisafe-dev-truststore.p12
 * </pre>
 */
@ConfigurationProperties(prefix = "aisafe")
public class AisafeProperties {

    /** Identificador desta réplica nos logs e na auditoria (ex.: flightroutes-1). */
    private String instanceId = "unknown";

    private final Peers peers = new Peers();
    private final Http http = new Http();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Security security = new Security();
    private final Tls tls = new Tls();

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public Peers getPeers() { return peers; }
    public Http getHttp() { return http; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Security getSecurity() { return security; }
    public Tls getTls() { return tls; }

    /** Réplicas do MESMO microsserviço (lista estática de peers, sem incluir a própria instância). */
    public static class Peers {
        private List<String> urls = new ArrayList<>();

        public List<String> getUrls() { return urls; }
        public void setUrls(List<String> urls) { this.urls = urls; }
    }

    /** Timeouts e retry de todas as chamadas HTTP entre serviços/réplicas. */
    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(1);
        private Duration readTimeout = Duration.ofSeconds(3);
        /** Tentativas extra após a primeira falha transitória (0 = sem retry). */
        private int maxRetries = 2;
        /** Espera antes do 1.º retry; duplica a cada tentativa (exponential backoff). */
        private Duration initialBackoff = Duration.ofMillis(100);
        /** Tempo máximo à espera das respostas de todos os peers numa agregação. */
        private Duration aggregationTimeout = Duration.ofSeconds(5);

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public Duration getAggregationTimeout() { return aggregationTimeout; }
        public void setAggregationTimeout(Duration aggregationTimeout) { this.aggregationTimeout = aggregationTimeout; }
    }

    public static class CircuitBreaker {
        /** Falhas consecutivas até o circuito abrir. */
        private int failureThreshold = 3;
        /** Tempo em que o destino deixa de ser chamado antes de nova tentativa (HALF_OPEN). */
        private Duration openDuration = Duration.ofSeconds(10);

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public Duration getOpenDuration() { return openDuration; }
        public void setOpenDuration(Duration openDuration) { this.openDuration = openDuration; }
    }

    public static class Security {
        /** Segredo partilhado que identifica os serviços AISafe entre si (header X-Service-Key). */
        private String serviceKey = "";
        private final DevToken devToken = new DevToken();

        public String getServiceKey() { return serviceKey; }
        public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
        public DevToken getDevToken() { return devToken; }

        /** POST /auth/dev-token — só para desenvolvimento/demonstração. */
        public static class DevToken {
            private boolean enabled = false;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    /** Truststore usado pelos clientes HTTP quando os serviços correm em HTTPS (perfil tls). */
    public static class Tls {
        private String trustStore;
        private String trustStorePassword;
        private String trustStoreType = "PKCS12";

        public String getTrustStore() { return trustStore; }
        public void setTrustStore(String trustStore) { this.trustStore = trustStore; }
        public String getTrustStorePassword() { return trustStorePassword; }
        public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
        public String getTrustStoreType() { return trustStoreType; }
        public void setTrustStoreType(String trustStoreType) { this.trustStoreType = trustStoreType; }
    }
}
