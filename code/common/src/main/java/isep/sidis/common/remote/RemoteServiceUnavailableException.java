package isep.sidis.common.remote;

public class RemoteServiceUnavailableException extends RuntimeException {
    public RemoteServiceUnavailableException(String service, Throwable cause) {
        super(service + " service is unavailable.", cause);
    }
}
