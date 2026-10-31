package isep.psoft.aisafe.exceptions;

import isep.sidis.common.remote.RemoteServiceUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/** Exceção → estado HTTP, com corpo {"error": "..."} (validação: {"campo": "mensagem"}). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Dados inválidos (enum desconhecido, datas, template não aplicável ao modelo, nome repetido) → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Parâmetro com tipo errado (ex.: from=abc) → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
    }

    // JSON mal formado ou com tipos errados (ex.: data inválida) → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body (check field types and ISO-8601 dates).");
    }

    // Registo / template / aeronave remota não encontrados → 404
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFoundException(EntityNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Conflito de negócio (registo já concluído, recategorizar um registo concluído) → 409
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Atualização concorrente detetada pelo @Version (optimistic locking) → 409
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "The resource was updated by another user. Please refresh and try again.");
    }

    // Bean Validation (@Valid) → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // Nenhuma réplica do Aircraft Management (ou nenhum peer, num comando encaminhado) respondeu → 503
    @ExceptionHandler(RemoteServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleRemoteServiceUnavailable(RemoteServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // Serviço remoto ou réplica dona recusou o pedido (401/403/409/...) → mesmo estado
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return error(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}
