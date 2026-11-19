package isep.sidis.common.messaging;

import java.util.regex.Pattern;

public final class MessagingNames {
    public static final String DEAD_LETTER_EXCHANGE = "aisafe.dlx";

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final Pattern NAME_PART = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private MessagingNames() {
    }

    public static String eventsExchange(String service) {
        return "aisafe." + part(service, "service") + ".events";
    }

    public static String routingKey(String aggregate, String action) {
        return part(aggregate, "aggregate") + "." + part(action, "action");
    }

    /**
     * Queue of one consuming instance for the events of one source service: {@code <instanceId>.<sourceService>},
     * e.g. {@code flightroutes-2.flightroutes}. The instance id already names the consuming service
     * ({@code <service>-<n>}), so it is not repeated.
     */
    public static String consumerQueue(String instanceId, String sourceService) {
        return part(instanceId, "instanceId") + "." + part(sourceService, "sourceService");
    }

    /** {@code <queue>.dlq}, e.g. {@code flightroutes-2.flightroutes.dlq}. */
    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }

    private static String part(String value, String name) {
        if (value == null || !NAME_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + NAME_PART.pattern() + " (got: " + value + ")");
        }
        return value;
    }
}
