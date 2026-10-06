package isep.sidis.common.resilience;

/** O destino está com o circuito aberto: o pedido nem chega a ser enviado. */
public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String target) {
        super("Circuit open for " + target);
    }
}
