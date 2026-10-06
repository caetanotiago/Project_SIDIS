package isep.sidis.common.peers;

import isep.sidis.common.resilience.CircuitBreaker;
import isep.sidis.common.resilience.CircuitBreakerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Componente "peers" do /actuator/health: estado do circuit breaker de cada réplica peer e de cada
 * serviço remoto já contactado. Fica sempre UP — um peer em baixo não torna ESTA réplica indisponível
 * (é precisamente isso que a replicação garante); serve para monitorização.
 */
public class PeersHealthIndicator implements HealthIndicator {

    private final PeerClient peerClient;
    private final CircuitBreakerRegistry breakers;

    public PeersHealthIndicator(PeerClient peerClient, CircuitBreakerRegistry breakers) {
        this.peerClient = peerClient;
        this.breakers = breakers;
    }

    @Override
    public Health health() {
        Map<String, String> peers = new LinkedHashMap<>();
        for (String peer : peerClient.getPeers()) {
            CircuitBreaker cb = breakers.all().get(peer);
            peers.put(peer, cb == null ? "NOT_CONTACTED_YET" : cb.getState().name());
        }
        Map<String, String> remotes = new LinkedHashMap<>();
        breakers.all().forEach((target, cb) -> {
            if (!peers.containsKey(target)) {
                remotes.put(target, cb.getState().name());
            }
        });
        return Health.up()
                .withDetail("peers", peers)
                .withDetail("remoteServices", remotes)
                .build();
    }
}
