package isep.sidis.common.messaging;

import java.util.List;

public class MessagingTopology {
    private final String consumerService;
    private final String instanceId;

    public MessagingTopology(String consumerService, String instanceId) {
        this.consumerService = consumerService;
        this.instanceId = instanceId;
    }

    public EventSubscription subscribe(String sourceService, String... routingKeyPatterns) {
        return new EventSubscription(
                MessagingNames.consumerQueue(instanceId, sourceService),
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
