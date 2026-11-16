package isep.sidis.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "aisafe")
public class AisafeProperties {
    private String instanceId = "unknown";

    private final Peers peers = new Peers();
    private final Http http = new Http();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Security security = new Security();
    private final Tls tls = new Tls();
    private final Messaging messaging = new Messaging();

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public Peers getPeers() { return peers; }
    public Http getHttp() { return http; }
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Security getSecurity() { return security; }
    public Tls getTls() { return tls; }
    public Messaging getMessaging() { return messaging; }

    public static class Peers {
        private List<String> urls = new ArrayList<>();

        public List<String> getUrls() { return urls; }
        public void setUrls(List<String> urls) { this.urls = urls; }
    }

    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(1);
        private Duration readTimeout = Duration.ofSeconds(3);
        private int maxRetries = 2;
        private Duration initialBackoff = Duration.ofMillis(100);
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
        private int failureThreshold = 3;
        private Duration openDuration = Duration.ofSeconds(10);

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public Duration getOpenDuration() { return openDuration; }
        public void setOpenDuration(Duration openDuration) { this.openDuration = openDuration; }
    }

    public static class Security {
        private String serviceKey = "";
        private List<String> serviceOnlyPaths = new ArrayList<>();
        private final DevToken devToken = new DevToken();

        public String getServiceKey() { return serviceKey; }
        public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
        public List<String> getServiceOnlyPaths() { return serviceOnlyPaths; }
        public void setServiceOnlyPaths(List<String> serviceOnlyPaths) { this.serviceOnlyPaths = serviceOnlyPaths; }
        public DevToken getDevToken() { return devToken; }

        public static class DevToken {
            private boolean enabled = false;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    public static class Messaging {
        private boolean enabled = false;
        private String serviceName;
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(5);
        private Duration confirmTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getInitialInterval() { return initialInterval; }
        public void setInitialInterval(Duration initialInterval) { this.initialInterval = initialInterval; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public Duration getMaxInterval() { return maxInterval; }
        public void setMaxInterval(Duration maxInterval) { this.maxInterval = maxInterval; }
        public Duration getConfirmTimeout() { return confirmTimeout; }
        public void setConfirmTimeout(Duration confirmTimeout) { this.confirmTimeout = confirmTimeout; }
    }

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
