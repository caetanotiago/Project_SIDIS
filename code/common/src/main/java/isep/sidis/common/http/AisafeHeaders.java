package isep.sidis.common.http;

/** Headers HTTP usados na comunicação entre serviços e entre réplicas. */
public final class AisafeHeaders {

    /** Identifica um pedido ao longo de todos os serviços/réplicas que atravessa (logs e auditoria). */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Segredo partilhado que autentica o serviço chamador (inter-service authentication). */
    public static final String SERVICE_KEY = "X-Service-Key";

    /** Réplica/serviço que originou a chamada (ex.: flightroutes-1). Só informativo (auditoria). */
    public static final String CALLER_INSTANCE = "X-Caller-Instance";

    /**
     * Nº de saltos peer-to-peer já feitos. Um pedido com este header veio de outra réplica e é
     * respondido APENAS com dados locais — impede forwarding circular (A → B → A).
     */
    public static final String PEER_HOPS = "X-Peer-Hops";

    /** Máximo de saltos: com a lista de peers completa (full mesh) basta 1. */
    public static final int MAX_PEER_HOPS = 1;

    /** Na resposta: "true" quando alguma réplica não respondeu e o resultado pode estar incompleto. */
    public static final String PARTIAL_RESPONSE = "X-Partial-Response";

    /** Na resposta: nº de réplicas que não responderam. */
    public static final String UNAVAILABLE_PEERS = "X-Unavailable-Peers";

    private AisafeHeaders() {
    }
}
