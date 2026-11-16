package isep.sidis.common.resilience;

public class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String target) {
        super("Circuit open for " + target);
    }
}
