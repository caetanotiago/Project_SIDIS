package isep.sidis.common.messaging;

import isep.sidis.common.DistributedInfrastructureAutoConfiguration;
import isep.sidis.common.config.AisafeProperties;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Clock;

@AutoConfiguration(afterName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AisafeProperties.class)
public class EventMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter aisafeEventMessageConverter() {
        return EventMessageConverters.messageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingTopology messagingTopology(AisafeProperties props, Environment env) {
        return new MessagingTopology(serviceName(props, env), DistributedInfrastructureAutoConfiguration.instanceId(props, env));
    }

    @Bean
    public Declarables aisafeMessagingExchanges(MessagingTopology topology) {
        return new Declarables(
                new TopicExchange(MessagingNames.eventsExchange(topology.getConsumerService()), true, false),
                new DirectExchange(MessagingNames.DEAD_LETTER_EXCHANGE, true, false));
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate, MessagingTopology topology, AisafeProperties props) {
        return new EventPublisher(rabbitTemplate, topology.getConsumerService(), topology.getInstanceId(),
                Clock.systemUTC(), props.getMessaging().getConfirmTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessedEventStore processedEventStore() {
        return new InMemoryProcessedEventStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentEventHandler idempotentEventHandler(ProcessedEventStore store) {
        return new IdempotentEventHandler(store);
    }

    @Bean
    public ContainerCustomizer<SimpleMessageListenerContainer> aisafeListenerContainerCustomizer(AisafeProperties props) {
        AisafeProperties.Messaging m = props.getMessaging();
        return container -> {
            container.setAcknowledgeMode(AcknowledgeMode.AUTO);
            container.setDefaultRequeueRejected(false);
            container.setConcurrentConsumers(1);
            container.setAdviceChain(ConsumerRetry.interceptor(
                    m.getMaxAttempts(), m.getInitialInterval(), m.getMultiplier(), m.getMaxInterval()));
        };
    }

    static String serviceName(AisafeProperties props, Environment env) {
        String name = props.getMessaging().getServiceName();
        return (name != null && !name.isBlank()) ? name : env.getProperty("spring.application.name", "service");
    }
}
