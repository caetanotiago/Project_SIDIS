package isep.psoft.aisafe.maintenance.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.remote.ReplicatedServiceClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Anti-Corruption Layer para o Aircraft Management (contratos B3a–B3d do Integration-Contracts).
 *
 * <p>Usa o {@link ReplicatedServiceClient} (round-robin + failover + retry + circuit breaker sobre as
 * 3 réplicas). O JWT do utilizador é reencaminhado pela biblioteca common. Tradução de erros:
 * 404 → {@link EntityNotFoundException}; outros 4xx → {@link ResponseStatusException} com o mesmo
 * estado e a mensagem do fornecedor; nenhuma réplica → RemoteServiceUnavailableException (503).
 */
@Component
public class AircraftClient {

    private static final int PAGE_SIZE = 100;
    private static final ParameterizedTypeReference<RemotePage<AircraftSnapshot>> AIRCRAFT_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<RemotePage<OperationalHours>> HOURS_PAGE =
            new ParameterizedTypeReference<>() { };

    private final ReplicatedServiceClient aircraftService;

    public AircraftClient(@Qualifier("aircraftService") ReplicatedServiceClient aircraftService) {
        this.aircraftService = aircraftService;
    }

    /** Página no formato PageDTO do Aircraft Management. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemotePage<T>(List<T> content, int totalPages) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OperationalHours(String registrationNumber, Double totalHours) { }

    record StatusUpdate(String status) { }

    /** Frota completa (B3d) + indicação de resposta parcial do Aircraft Management. */
    public record Fleet(List<AircraftSnapshot> aircraft, boolean partial) { }

    /** Horas de voo por matrícula (B3d, US206) + indicação de resposta parcial. */
    public record FlightHours(Map<String, Double> hoursByRegistration, boolean partial) {
        public double of(String registration) {
            return hoursByRegistration.getOrDefault(registration.toUpperCase(), 0.0);
        }
    }

    // ------------------------------------------------------------------ B3a

    /** B3a: GET /api/aircrafts/{registrationNumber} — a aeronave existe e qual o seu modelo/estado. */
    public AircraftSnapshot getAircraft(String registrationNumber) {
        return translate(() -> aircraftService.call((rest, baseUrl) -> rest.get()
                        .uri(baseUrl + "/api/aircrafts/{reg}", registrationNumber)
                        .retrieve()
                        .body(AircraftSnapshot.class)),
                "Aircraft not found: " + registrationNumber);
    }

    // ------------------------------------------------------------------ B3b

    /** B3b: PATCH /api/aircrafts/{registrationNumber}/status — idempotente (valor fixo). */
    public AircraftSnapshot updateStatus(String registrationNumber, String status) {
        return translate(() -> aircraftService.call((rest, baseUrl) -> rest.patch()
                        .uri(baseUrl + "/api/aircrafts/{reg}/status", registrationNumber)
                        .body(new StatusUpdate(status))
                        .retrieve()
                        .body(AircraftSnapshot.class)),
                "Aircraft not found: " + registrationNumber);
    }

    // ------------------------------------------------------------------ B3c

    /** B3c: GET /api/aircraft-models/{modelName} — 200 existe, 404 não existe. */
    public boolean modelExists(String modelName) {
        try {
            translate(() -> aircraftService.call((rest, baseUrl) -> rest.get()
                            .uri(baseUrl + "/api/aircraft-models/{model}", modelName)
                            .retrieve()
                            .toBodilessEntity()),
                    "Aircraft model not found: " + modelName);
            return true;
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ B3d

    /** B3d: GET /api/aircrafts?page=&size= até à última página. */
    public Fleet fleet() {
        List<AircraftSnapshot> all = new ArrayList<>();
        boolean partial = false;
        int page = 0;
        int totalPages;
        do {
            ResponseEntity<RemotePage<AircraftSnapshot>> response = getPage("/api/aircrafts", page, AIRCRAFT_PAGE);
            partial |= isPartial(response);
            RemotePage<AircraftSnapshot> body = response.getBody();
            if (body == null || body.content() == null) {
                break;
            }
            all.addAll(body.content());
            totalPages = body.totalPages();
            page++;
        } while (page < totalPages);
        return new Fleet(all, partial);
    }

    /** B3d: GET /api/aircrafts/operational-hours?page=&size= até à última página. */
    public FlightHours flightHours() {
        Map<String, Double> hours = new HashMap<>();
        boolean partial = false;
        int page = 0;
        int totalPages;
        do {
            ResponseEntity<RemotePage<OperationalHours>> response =
                    getPage("/api/aircrafts/operational-hours", page, HOURS_PAGE);
            partial |= isPartial(response);
            RemotePage<OperationalHours> body = response.getBody();
            if (body == null || body.content() == null) {
                break;
            }
            body.content().forEach(h -> hours.put(h.registrationNumber().toUpperCase(),
                    h.totalHours() == null ? 0.0 : h.totalHours()));
            totalPages = body.totalPages();
            page++;
        } while (page < totalPages);
        return new FlightHours(hours, partial);
    }

    // ------------------------------------------------------------------ internos

    private <T> ResponseEntity<RemotePage<T>> getPage(String path, int page,
                                                      ParameterizedTypeReference<RemotePage<T>> type) {
        return translate(() -> aircraftService.call((rest, baseUrl) -> rest.get()
                        .uri(baseUrl + path + "?page={page}&size={size}", page, PAGE_SIZE)
                        .retrieve()
                        .toEntity(type)),
                "Not found: " + path);
    }

    private static <T> T translate(Supplier<T> call, String notFoundMessage) {
        try {
            return call.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException(notFoundMessage);
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
            throw new ResponseStatusException(status, "Aircraft Management: " + errorMessage(e), e);
        }
    }

    private static boolean isPartial(ResponseEntity<?> response) {
        return "true".equalsIgnoreCase(response.getHeaders().getFirst(AisafeHeaders.PARTIAL_RESPONSE));
    }

    private static String errorMessage(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null && body.get("error") != null) {
                return String.valueOf(body.get("error"));
            }
        } catch (RuntimeException ignored) {
            // corpo vazio ou não JSON
        }
        return e.getStatusText().isBlank() ? "request rejected (" + e.getStatusCode().value() + ")" : e.getStatusText();
    }
}
