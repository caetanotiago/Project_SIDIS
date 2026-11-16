package isep.sidis.common.http;

public final class AisafeHeaders {
    public static final String CORRELATION_ID = "X-Correlation-Id";

    public static final String SERVICE_KEY = "X-Service-Key";

    public static final String CALLER_INSTANCE = "X-Caller-Instance";

    public static final String PEER_HOPS = "X-Peer-Hops";

    public static final int MAX_PEER_HOPS = 1;

    public static final String PARTIAL_RESPONSE = "X-Partial-Response";

    public static final String UNAVAILABLE_PEERS = "X-Unavailable-Peers";

    private AisafeHeaders() {
    }
}
