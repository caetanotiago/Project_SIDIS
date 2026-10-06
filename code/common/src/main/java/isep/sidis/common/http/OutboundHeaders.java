package isep.sidis.common.http;

import isep.sidis.common.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Headers a enviar numa chamada a outro serviço/réplica, calculados a partir do pedido em curso:
 * <ul>
 *   <li>{@code Authorization} — o JWT do utilizador, para o destino aplicar o mesmo controlo de acessos;</li>
 *   <li>{@code X-Correlation-Id} — para seguir o pedido nos logs de todos os serviços;</li>
 *   <li>{@code X-Service-Key} e {@code X-Caller-Instance} — identidade do serviço chamador.</li>
 * </ul>
 * Tem de ser chamado na thread do pedido HTTP (usa RequestContextHolder/MDC); o PeerClient
 * captura-os antes de lançar as chamadas em paralelo.
 */
public class OutboundHeaders {

    private final String serviceKey;
    private final String instanceId;

    public OutboundHeaders(String serviceKey, String instanceId) {
        this.serviceKey = serviceKey;
        this.instanceId = instanceId;
    }

    public Map<String, String> capture() {
        Map<String, String> headers = new LinkedHashMap<>();
        currentRequest().map(r -> r.getHeader(HttpHeaders.AUTHORIZATION))
                .ifPresent(auth -> headers.put(HttpHeaders.AUTHORIZATION, auth));

        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        if (correlationId != null) {
            headers.put(AisafeHeaders.CORRELATION_ID, correlationId);
        }
        if (serviceKey != null && !serviceKey.isBlank()) {
            headers.put(AisafeHeaders.SERVICE_KEY, serviceKey);
        }
        headers.put(AisafeHeaders.CALLER_INSTANCE, instanceId);
        return headers;
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }
}
