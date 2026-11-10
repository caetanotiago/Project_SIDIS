package isep.sidis.common.messaging;

import java.util.List;

/**
 * Builds the broker topology of this instance following {@link MessagingNames}. Auto-configured as a bean
 * (when {@code aisafe.messaging.enabled=true}) with this service's name and instance id.
 *
 * <pre>
 * &#64;Bean
 * EventSubscription flightRoutesEvents(MessagingTopology topology) {
 *     return topology.subscribe("flightroutes", "route.*");   // queue maintenance.maintenance-1.flightroutes
 * }
 * </pre>
 */
public class MessagingTopology {

    private final String consumerService;
    private final String instanceId;

    public MessagingTopology(String consumerService, String instanceId) {
        this.consumerService = consumerService;
        this.instanceId = instanceId;
    }

    /**
     * Subscribes THIS instance to the events of {@code sourceService} whose routing key matches any pattern
     * (topic syntax: {@code *} = exactly one word, {@code #} = zero or more words).
     */
    public EventSubscription subscribe(String sourceService, String... routingKeyPatterns) {
        return new EventSubscription(
                MessagingNames.consumerQueue(consumerService, instanceId, sourceService),
                MessagingNames.eventsExchange(sourceService),
                List.of(routingKeyPatterns));
    }

    public String getConsumerService() {
        return consumerService;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
