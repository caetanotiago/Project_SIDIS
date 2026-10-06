package isep.sidis.common.observability;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Audit log (Access Control and Audit): uma linha por pedido à API com quem (utilizador e roles do
 * JWT), o quê (método e caminho), o resultado (status HTTP), de onde (cliente, ou réplica/serviço
 * chamador) e o correlation id. Escreve no logger {@code AUDIT}, que pode ir para um ficheiro próprio.
 *
 * <p>Corre dentro da SecurityFilterChain, logo depois do SecurityContextHolderFilter: quando o pedido
 * termina o utilizador autenticado ainda está no contexto e o status (incluindo 401/403) é o final.
 */
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs") || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String user = auth == null || !auth.isAuthenticated() ? "anonymous" : auth.getName();
            String roles = auth == null ? "" : auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .collect(Collectors.joining(","));
            String caller = request.getHeader(AisafeHeaders.CALLER_INSTANCE);
            String source = request.getHeader(AisafeHeaders.PEER_HOPS) != null
                    ? "peer:" + caller
                    : caller != null ? "service:" + caller : "client:" + request.getRemoteAddr();
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();

            audit.info("user={} roles=[{}] {} {}{} status={} source={} durationMs={}",
                    sanitize(user), roles, request.getMethod(), request.getRequestURI(), sanitize(query),
                    response.getStatus(), sanitize(source), (System.nanoTime() - start) / 1_000_000);
        }
    }

    // Remove quebras de linha de valores controlados pelo cliente (log injection).
    private static String sanitize(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }
}
