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

    public static String consumerQueue(String consumerService, String instanceId, String sourceService) {
        return part(consumerService, "consumerService") + "." + part(instanceId, "instanceId") + "."
                + part(sourceService, "sourceService");
    }

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
