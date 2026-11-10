package isep.sidis.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Wire format of the envelope: what any consumer, in any service, will receive. */
class IntegrationEventSerializationTest {

    record SamplePayload(String id, double distance, Integer minCapacity) { }

    private static final Instant WHEN = Instant.parse("2026-11-10T14:03:21.512Z");
    private static final UUID ID = UUID.fromString("5b0f6c1e-2a4b-4f7e-9d6a-1c2b3d4e5f60");

    private final MessageConverter converter = EventMessageConverters.messageConverter();
    private final JsonMapper json = EventMessageConverters.jsonMapper();

    private static IntegrationEvent<SamplePayload> sample() {
        return new IntegrationEvent<>(ID, "RouteCreated", WHEN, "flightroutes", "flightroutes-1", "r-1",
                new SamplePayload("r-1", 274.5, 120));
    }

    @Test
    void envelopeIsSerialisedWithAllFieldsAndAnIsoUtcTimestamp() {
        Message message = converter.toMessage(sample(), new MessageProperties());

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        JsonNode body = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals(ID.toString(), body.get("eventId").asString());
        assertEquals("RouteCreated", body.get("eventType").asString());
        assertTrue(body.get("timestamp").isString(), "timestamp must be a string, not a number: " + body);
        assertEquals("2026-11-10T14:03:21.512Z", body.get("timestamp").asString());
        assertEquals("flightroutes", body.get("sourceService").asString());
        assertEquals("flightroutes-1", body.get("sourceInstance").asString());
        assertEquals("r-1", body.get("aggregateId").asString());
        assertEquals(274.5, body.get("payload").get("distance").asDouble());
        assertEquals(7, body.size(), "exactly the 7 envelope fields: " + body);
    }

    @Test
    void consumerGetsATypedPayloadFromTheListenerParameterType() {
        Message message = converter.toMessage(sample(), new MessageProperties());
        // What @RabbitListener does with a parameter of type IntegrationEvent<SamplePayload>
        message.getMessageProperties().setInferredArgumentType(
                ResolvableType.forClassWithGenerics(IntegrationEvent.class, SamplePayload.class).getType());

        Object converted = converter.fromMessage(message);

        IntegrationEvent<?> event = assertInstanceOf(IntegrationEvent.class, converted);
        assertEquals(sample(), event);
        assertInstanceOf(SamplePayload.class, event.payload());
    }

    @Test
    void unknownFieldsAreIgnoredSoProducersCanAddFields() {
        String body = """
                {"eventId":"5b0f6c1e-2a4b-4f7e-9d6a-1c2b3d4e5f60","eventType":"RouteCreated",
                 "timestamp":"2026-11-10T14:03:21.512Z","sourceService":"flightroutes",
                 "sourceInstance":"flightroutes-1","aggregateId":"r-1","schemaVersion":2,
                 "payload":{"id":"r-1","distance":274.5,"minCapacity":120,"newField":"x"}}""";
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setInferredArgumentType(
                ResolvableType.forClassWithGenerics(IntegrationEvent.class, SamplePayload.class).getType());

        Object converted = converter.fromMessage(new Message(body.getBytes(StandardCharsets.UTF_8), props));

        assertEquals(sample(), converted);
    }

    @Test
    void requiredMetadataIsValidated() {
        assertThrows(NullPointerException.class,
                () -> new IntegrationEvent<>(null, "X", WHEN, "s", "s-1", "a", null));
        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationEvent<>(ID, " ", WHEN, "s", "s-1", "a", null));
        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationEvent<>(ID, "X", WHEN, "s", "s-1", null, null));
    }

    @Test
    void createGivesEveryEventANewIdAndIsFromComparesTheInstance() {
        IntegrationEvent<String> a = IntegrationEvent.create("X", WHEN, "s", "s-1", "agg", "p");
        IntegrationEvent<String> b = IntegrationEvent.create("X", WHEN, "s", "s-1", "agg", "p");

        assertNotEquals(a.eventId(), b.eventId());
        assertTrue(a.isFrom("s-1"));
        assertFalse(a.isFrom("s-2"));
    }
}
