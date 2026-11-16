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
