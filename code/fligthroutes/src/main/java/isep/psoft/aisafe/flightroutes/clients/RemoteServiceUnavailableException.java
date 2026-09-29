package isep.psoft.aisafe.flightroutes.clients;

// Um microsserviço remoto não respondeu ou devolveu um erro inesperado → 503 Service Unavailable
public class RemoteServiceUnavailableException extends RuntimeException {

    public RemoteServiceUnavailableException(String service, Throwable cause) {
        super(service + " service is unavailable.", cause);
    }
}
