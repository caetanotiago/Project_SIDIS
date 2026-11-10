package isep.sidis.common.messaging;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON mapper and AMQP message converter shared by all services, so every event has the same wire format.
 *
 * <p>Jackson 3 (Spring Boot 4) includes {@code java.time} support in jackson-databind: the Jackson 2
 * {@code JavaTimeModule} is no longer a separate module. {@link java.time.Instant} is written as an ISO-8601
 * string in UTC ({@code 2026-11-10T14:03:21.512Z}), never as a number.
 */
public final class EventMessageConverters {

    private EventMessageConverters() {
    }

    public static JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Tolerant reader: a consumer ignores fields it does not know, so producers can add fields.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Converter for listeners and {@link org.springframework.amqp.rabbit.core.RabbitTemplate}. The target type of a
     * {@code @RabbitListener} method parameter, including generics ({@code IntegrationEvent<RoutePayload>}), is
     * inferred from the method signature, so producer and consumer do not need to share payload classes.
     */
    public static JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter(jsonMapper(), "isep.sidis.common.messaging", "java.util", "java.lang");
    }
}
