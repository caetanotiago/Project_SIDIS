package isep.sidis.common.security;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Inter-service authentication. Os serviços AISafe partilham um segredo ({@code aisafe.security.service-key})
 * enviado no header {@code X-Service-Key}:
 * <ul>
 *   <li>um pedido peer-to-peer ({@code X-Peer-Hops}) TEM de trazer a chave válida — caso contrário
 *       qualquer cliente podia fingir ser uma réplica e obter só dados locais / contornar o encaminhamento;</li>
 *   <li>um pedido a um caminho só para serviços ({@code aisafe.security.service-only-paths}) TEM de trazer
 *       a chave válida — são endpoints que só outros serviços AISafe consomem (ex.: estatísticas em bruto);</li>
 *   <li>se um pedido trouxer a chave, ela tem de estar correta;</li>
 *   <li>{@code X-Peer-Hops} acima do máximo é rejeitado (proteção contra ciclos).</li>
 * </ul>
 * A autorização do utilizador continua a ser feita pelo JWT (reencaminhado pela réplica chamadora).
 */
public class ServiceKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyFilter.class);

    private final byte[] expectedKey;
    private final List<PathPattern> serviceOnlyPaths;

    public ServiceKeyFilter(String serviceKey) {
        this(serviceKey, List.of());
    }

    public ServiceKeyFilter(String serviceKey, List<String> serviceOnlyPaths) {
        this.expectedKey = serviceKey == null ? new byte[0] : serviceKey.getBytes(StandardCharsets.UTF_8);
        // Padrão inválido → exceção no arranque (melhor do que um endpoint desprotegido sem aviso)
        this.serviceOnlyPaths = serviceOnlyPaths == null ? List.of()
                : serviceOnlyPaths.stream().map(String::trim).filter(p -> !p.isEmpty())
                        .map(PathPatternParser.defaultInstance::parse).toList();
        if (!this.serviceOnlyPaths.isEmpty() && expectedKey.length == 0) {
            log.warn("Service-only paths configured but aisafe.security.service-key is empty: they will reject every request.");
        }
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
        if (key == null && isServiceOnly(request)) {
            log.warn("Rejected request to a service-only endpoint without service key: {} {}",
                    request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "This endpoint is only available to AISafe services.");
            return;
        }
        if (key != null && !matches(key)) {
            log.warn("Rejected request with invalid service key: {} {}", request.getMethod(), request.getRequestURI());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid service key.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isServiceOnly(HttpServletRequest request) {
        if (serviceOnlyPaths.isEmpty()) {
            return false;
        }
        // Caminho sem context path; o PathContainer descodifica os segmentos (%xx) e ignora ";params",
        // por isso variantes codificadas do mesmo caminho também são apanhadas
        String path = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer container = PathContainer.parsePath(path);
        return serviceOnlyPaths.stream().anyMatch(p -> p.matches(container));
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
