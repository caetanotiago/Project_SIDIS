package isep.sidis.common.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class DevTokenController {
    public static final Set<String> ROLES = Set.of(
            "ADMIN", "BACKOFFICE_OPERATOR", "ATCC", "MAINTENANCE_TECHNICIAN", "MAINTENANCE_SUPERVISOR");

    private final JwtTokenProvider tokenProvider;

    public DevTokenController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public record DevTokenRequest(String username, List<String> roles) {
    }

    @PostMapping("/auth/dev-token")
    public ResponseEntity<Map<String, String>> issue(@RequestBody DevTokenRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.roles() == null || request.roles().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and roles are required"));
        }
        List<String> authorities = request.roles().stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .map(String::toUpperCase)
                .toList();
        if (!ROLES.containsAll(authorities)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown role. Allowed: " + ROLES));
        }
        String token = tokenProvider.generateToken(request.username(),
                authorities.stream().map(r -> "ROLE_" + r).toList());
        return ResponseEntity.ok(Map.of("token", token, "tokenType", "Bearer"));
    }
}
