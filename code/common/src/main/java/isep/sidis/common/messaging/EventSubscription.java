package isep.sidis.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * The broker objects one consuming instance needs to receive the events of one source service:
 * <ul>
 *   <li>the source's topic exchange {@code aisafe.<source>.events} (declared here too, so the consumer can start
 *       before the producer; declaring an existing exchange with the same settings is a no-op);</li>
 *   <li>the durable, non-exclusive queue {@code <consumer>.<instanceId>.<source>}, bound with each routing-key
 *       pattern ({@code route.*}, {@code route.created}, {@code #}, ...). Each replica has its own queue, so every
 *       replica receives every matching event, and messages wait in the queue while the replica is down;</li>
 *   <li>its dead-letter queue {@code <queue>.dlq}, bound to the shared {@code aisafe.dlx} exchange; the queue
 *       sends rejected messages (retries exhausted) there.</li>
 * </ul>
 *
 * <p>It is a {@link Declarables} bean: Spring's {@code RabbitAdmin} declares everything on the broker at startup.
 * Create it with {@link MessagingTopology#subscribe(String, String...)} and listen with
 * {@code @RabbitListener(queues = "#{beanName.queueName}")}.
 */
public class EventSubscription extends Declarables {

    private final String queueName;
    private final String deadLetterQueueName;
    private final String sourceExchange;

    EventSubscription(String queueName, String sourceExchange, List<String> routingKeyPatterns) {
        super(declarables(queueName, sourceExchange, routingKeyPatterns));
        this.queueName = queueName;
        this.deadLetterQueueName = MessagingNames.deadLetterQueue(queueName);
        this.sourceExchange = sourceExchange;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getDeadLetterQueueName() {
        return deadLetterQueueName;
    }

    public String getSourceExchange() {
        return sourceExchange;
    }

    private static List<Declarable> declarables(String queueName, String sourceExchange, List<String> patterns) {
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("At least one routing-key pattern is required (e.g. \"route.*\" or \"#\")");
        }
        String dlqName = MessagingNames.deadLetterQueue(queueName);
        TopicExchange source = new TopicExchange(sourceExchange, true, false);
        DirectExchange dlx = new DirectExchange(MessagingNames.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(MessagingNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queueName)
                .build();
        Queue dlq = QueueBuilder.durable(dlqName).build();

        List<Declarable> all = new ArrayList<>(List.of(source, dlx, queue, dlq));
        all.add(BindingBuilder.bind(dlq).to(dlx).with(queueName));
        for (String pattern : patterns) {
            Binding binding = BindingBuilder.bind(queue).to(source).with(pattern);
            all.add(binding);
        }
        return all;
    }
}
