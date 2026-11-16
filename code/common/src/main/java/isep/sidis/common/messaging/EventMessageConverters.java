package isep.sidis.common.messaging;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class EventMessageConverters {
    private EventMessageConverters() {
    }

    public static JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public static JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter(jsonMapper(), "isep.sidis.common.messaging", "java.util", "java.lang");
    }
}
