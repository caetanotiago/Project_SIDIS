package isep.sidis.common.messaging;

import java.util.regex.Pattern;

/**
 * Naming rules for exchanges, routing keys and queues (docs/Messaging-Conventions.md). Every service
 * builds its names here, so all of them follow the same convention.
 *
 * <table>
 *   <caption>Names</caption>
 *   <tr><th>What</th><th>Rule</th><th>Example</th></tr>
 *   <tr><td>events exchange (topic, durable), one per producing service</td>
 *       <td>{@code aisafe.<service>.events}</td><td>{@code aisafe.flightroutes.events}</td></tr>
 *   <tr><td>routing key</td><td>{@code <aggregate>.<action>}</td><td>{@code route.created}</td></tr>
 *   <tr><td>queue (durable, non-exclusive), one per consuming instance and source service</td>
 *       <td>{@code <consumerService>.<instanceId>.<sourceService>}</td>
 *       <td>{@code flightroutes.flightroutes-2.flightroutes}</td></tr>
 *   <tr><td>dead-letter exchange (direct, durable), shared</td><td>{@code aisafe.dlx}</td><td></td></tr>
 *   <tr><td>dead-letter queue, one per consumer queue</td><td>{@code <queue>.dlq}</td>
 *       <td>{@code flightroutes.flightroutes-2.flightroutes.dlq}</td></tr>
 * </table>
 */
public final class MessagingNames {

    /** Shared dead-letter exchange (direct). Each DLQ is bound to it with its consumer queue name as routing key. */
    public static final String DEAD_LETTER_EXCHANGE = "aisafe.dlx";

    /** Message header with the HTTP correlation id of the request that caused the event (if any). */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final Pattern NAME_PART = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private MessagingNames() {
    }

    /** {@code aisafe.<service>.events} */
    public static String eventsExchange(String service) {
        return "aisafe." + part(service, "service") + ".events";
    }

    /** {@code <aggregate>.<action>}, e.g. {@code routingKey("route", "created")} = {@code route.created}. */
    public static String routingKey(String aggregate, String action) {
        return part(aggregate, "aggregate") + "." + part(action, "action");
    }

    /** {@code <consumerService>.<instanceId>.<sourceService>} */
    public static String consumerQueue(String consumerService, String instanceId, String sourceService) {
        return part(consumerService, "consumerService") + "." + part(instanceId, "instanceId") + "."
                + part(sourceService, "sourceService");
    }

    /** {@code <queue>.dlq} */
    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }

    /** Lower case letters, digits and '-'; no dots, because dots separate the parts of a name. */
    private static String part(String value, String name) {
        if (value == null || !NAME_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + NAME_PART.pattern() + " (got: " + value + ")");
        }
        return value;
    }
}
