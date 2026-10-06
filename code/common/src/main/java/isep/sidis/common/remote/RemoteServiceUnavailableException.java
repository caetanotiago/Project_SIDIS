package isep.sidis.common.remote;

/**
 * Nenhuma réplica de um microsserviço remoto respondeu (timeout, ligação recusada, 5xx ou
 * circuito aberto em todas). Cada serviço mapeia-a para 503 Service Unavailable.
 */
public class RemoteServiceUnavailableException extends RuntimeException {

    public RemoteServiceUnavailableException(String service, Throwable cause) {
        super(service + " service is unavailable.", cause);
    }
}
