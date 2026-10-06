package isep.sidis.common.observability;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Atribui a cada pedido um correlation id (o recebido em {@code X-Correlation-Id} ou um novo) e
 * coloca-o, com o id da instância, no MDC dos logs. O id é devolvido na resposta e reencaminhado
 * para os outros serviços/réplicas, por isso um pedido pode ser seguido em todos os logs.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_INSTANCE = "instance";

    // Aceita só ids "bem comportados" vindos de fora (evita log injection).
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String instanceId;

    public CorrelationIdFilter(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(AisafeHeaders.CORRELATION_ID);
        String correlationId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_INSTANCE, instanceId);
        response.setHeader(AisafeHeaders.CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_INSTANCE);
        }
    }
}
