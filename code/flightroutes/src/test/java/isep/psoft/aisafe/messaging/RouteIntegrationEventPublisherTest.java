package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.flightroutes.command.RouteChanged;
import isep.psoft.aisafe.flightroutes.command.RouteIntegrationEventPublisher;
import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.factories.FlightRouteFactory;
import isep.sidis.common.messaging.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Mapping of a committed route change to its integration event (the broker is mocked). */
class RouteIntegrationEventPublisherTest {

    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final RouteIntegrationEventPublisher publisher = new RouteIntegrationEventPublisher(eventPublisher);
    private final FlightRoute route = new FlightRouteFactory().createRoute("opo", "lis", 274.0, 400.0, 120, 55);

    @Test
    void aCreatedRouteIsPublishedAsRouteCreatedWithItsFullState() {
        publisher.on(RouteChanged.created(route));

        RouteEventPayload expected = new RouteEventPayload(null, "OPO", "LIS", 274.0, 400.0, 120, 55, "ACTIVE", null);
        verify(eventPublisher).publish(eq("RouteCreated"), eq("route.created"), isNull(), eq(expected));
    }

    @Test
    void anUpdatedRouteIsPublishedAsRouteUpdated() {
        route.changeStatus("INACTIVE");

        publisher.on(RouteChanged.updated(route));

        verify(eventPublisher).publish(eq("RouteUpdated"), eq("route.updated"), isNull(),
                eq(new RouteEventPayload(null, "OPO", "LIS", 274.0, 400.0, 120, 55, "INACTIVE", null)));
    }

    @Test
    void aBrokerFailureIsLoggedAndDoesNotFailTheAlreadyCommittedRequest() {
        when(eventPublisher.publish(any(), any(), any(), any()))
                .thenThrow(new AmqpConnectException(new ConnectException("Connection refused")));

        assertDoesNotThrow(() -> publisher.on(RouteChanged.created(route)));
    }
}
