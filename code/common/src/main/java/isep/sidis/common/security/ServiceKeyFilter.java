package isep.sidis.common.security;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Inter-service authentication. Os serviços AISafe partilham um segredo ({@code aisafe.security.service-key})
 * enviado no header {@code X-Service-Key}:
 * <ul>
 *   <li>um pedido peer-to-peer ({@code X-Peer-Hops}) TEM de trazer a chave válida — caso contrário
 *       qualquer cliente podia fingir ser uma réplica e obter só dados locais / contornar o encaminhamento;</li>
 *   <li>se um pedido trouxer a chave, ela tem de estar correta;</li>
 *   <li>{@code X-Peer-Hops} acima do máximo é rejeitado (proteção contra ciclos).</li>
 * </ul>
 * A autorização do utilizador continua a ser feita pelo JWT (reencaminhado pela réplica chamadora).
 */
public class ServiceKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyFilter.class);

    private final byte[] expectedKey;

    public ServiceKeyFilter(String serviceKey) {
        this.expectedKey = serviceKey == null ? new byte[0] : serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String hops = request.getHeader(AisafeHeaders.PEER_HOPS);
        String key = request.getHeader(AisafeHeaders.SERVICE_KEY);

        if (hops != null) {
            if (!validHops(hops)) {
                reject(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid " + AisafeHeaders.PEER_HOPS + " header.");
                return;
            }
            if (key == null) {
                log.warn("Rejected peer request without service key: {} {}", request.getMethod(), request.getRequestURI());
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Peer requests require a valid service key.");
                return;
            }
        }
        if (key != null && !matches(key)) {
            log.warn("Rejected request with invalid service key: {} {}", request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid service key.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String key) {
        // Comparação em tempo constante (não revela o prefixo correto por timing)
        return expectedKey.length > 0
                && MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean validHops(String hops) {
        try {
            int h = Integer.parseInt(hops);
            return h >= 1 && h <= AisafeHeaders.MAX_PEER_HOPS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
