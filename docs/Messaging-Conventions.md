# Messaging Conventions (RabbitMQ / AMQP)

How AISafe services talk to each other **asynchronously** (Assignment 2). External clients still use HTTP REST; between services and between replicas we publish and consume **events** through RabbitMQ.

Everything below is implemented in the shared library (`code/common`, package `isep.sidis.common.messaging`). Flight Routes is the working example (`code/flightroutes`). **Copy it; do not write your own AMQP code.**

| Contents |
|---|
| [1. Start RabbitMQ](#1-start-rabbitmq) · [2. The envelope](#2-the-envelope) · [3. Names](#3-names-exchanges-routing-keys-queues) · [4. Publish an event](#4-how-to-publish-an-event-from-your-service) · [5. Consume events](#5-how-to-consume-events-in-your-service) · [6. Reliability rules](#6-reliability-rules) · [7. Topic vs fanout](#7-design-decision-topic-exchange-not-fanout) · [8. Consistency model](#8-consistency-model) · [9. Flight Routes proof of concept](#9-flight-routes-proof-of-concept) · [10. Troubleshooting](#10-troubleshooting) |

---

## 1. Start RabbitMQ

Requirements: Docker (Docker Desktop on Windows/macOS).

1. **Once:** copy `infra/.env.example` to `infra/.env` and replace the placeholder user and password. `infra/.env` is ignored by git; never commit it. (There is no `guest/guest` account.)
2. **Start** (from the repository root):
   ```bash
   docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env up -d --wait
   ```
   You do not have to do this by hand: `code/scripts/start-all.ps1` / `start-all.sh` check that RabbitMQ is reachable and start it with this command if it is not. They also export the variables of `infra/.env` to every replica. If Docker is missing, `infra/.env` is missing or the broker does not come up, they stop with a message saying what to do. They never hang.
3. **Management UI:** <http://localhost:15672>. Log in with `RABBITMQ_USER` / `RABBITMQ_PASSWORD` from `infra/.env`. The **Exchanges** and **Queues** tabs show everything described in section 3.
4. **Stop:** `.\scripts\stop-all.ps1 -Broker` or `./scripts/stop-all.sh --broker` (from `code/`) stops the replicas and the broker. Without the flag the broker keeps running. Data is kept in the Docker volume `aisafe_rabbitmq-data`; `docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env down -v` erases it.

**Running without a broker:** set `AISAFE_MESSAGING_ENABLED=false` before `start-all` (or before `java -jar`). The service then neither publishes nor consumes events, and its `/actuator/health` does not include RabbitMQ.

| Variable (`infra/.env` or environment) | Default | Used by |
|---|---|---|
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | none (required) | broker account, `spring.rabbitmq.username/password` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` | `spring.rabbitmq.host/port`, scripts |
| `AISAFE_MESSAGING_ENABLED` | `true` (Flight Routes) | `aisafe.messaging.enabled` |

---

## 2. The envelope

Every message body is an `IntegrationEvent<T>` (Java record in `common`), serialised as JSON:

| Field | Type | Meaning |
|---|---|---|
| `eventId` | UUID string | Unique id of this event. Consumers use it to **ignore duplicates** |
| `eventType` | string | What happened, in past tense, PascalCase: `RouteCreated`, `AircraftStatusChanged` |
| `timestamp` | ISO-8601 string, UTC | When the event was created, e.g. `2026-11-10T14:03:21.512Z` |
| `sourceService` | string | Producing service = its `spring.application.name`, e.g. `flightroutes` |
| `sourceInstance` | string | Producing replica = its `aisafe.instance-id`, e.g. `flightroutes-1` |
| `aggregateId` | string | Id of the entity the event is about: route id, registration, IATA code, ... |
| `payload` | object | Event data. Defined and documented by the producer |

```json
{
  "eventId": "5b0f6c1e-2a4b-4f7e-9d6a-1c2b3d4e5f60",
  "eventType": "RouteCreated",
  "timestamp": "2026-11-10T14:03:21.512Z",
  "sourceService": "flightroutes",
  "sourceInstance": "flightroutes-1",
  "aggregateId": "8c1d2f9e-4b7a-4e43-9f0e-2d6a1b3c5e77",
  "payload": {
    "routeId": "8c1d2f9e-4b7a-4e43-9f0e-2d6a1b3c5e77",
    "originIata": "OPO", "destinationIata": "LIS",
    "distance": 274.0, "minRange": 400.0, "minCapacity": 120, "estimatedFlightTime": 55,
    "status": "ACTIVE", "version": 0
  }
}
```

Rules:
- **You never build the envelope yourself.** `EventPublisher` fills `eventId`, `timestamp`, `sourceService` and `sourceInstance`.
- **Payloads carry state, not just ids** ("state-carrying events"): put in the payload everything a consumer needs, so it never has to call you back over HTTP.
- **Tolerant reader:** consumers ignore fields they do not know. You may **add** payload fields at any time. **Renaming or removing** a field breaks consumers: agree on it with them first.
- The AMQP message also has `message_id` = `eventId`, `type` = `eventType`, `app_id` = `sourceService`, persistent delivery mode and, if the event comes from an HTTP request, the header `X-Correlation-Id`, so the logs of producer and consumer can be matched.
- JSON uses Jackson 3 (Spring Boot 4). `java.time` support is built into Jackson 3, so the Jackson 2 `JavaTimeModule` is not needed. Dates are written as ISO strings, never as numbers (`EventMessageConverters`).

---

## 3. Names: exchanges, routing keys, queues

Names are built by `MessagingNames` (`common`). Name parts are lower case, digits and `-`, with **no dots**, because dots separate the parts.

| Object | Rule | Flight Routes example |
|---|---|---|
| Events exchange: **topic**, durable. One per **producing** service | `aisafe.<service>.events` | `aisafe.flightroutes.events` |
| Routing key | `<aggregate>.<action>` | `route.created`, `route.updated`, `route.deleted` |
| Queue: durable, non-exclusive, not auto-delete. One per **consuming instance** per source service | `<consumerService>.<instanceId>.<sourceService>` | `flightroutes.flightroutes-2.flightroutes` (replica 2 of Flight Routes, reading Flight Routes' events) |
| Dead-letter exchange: direct, durable, **shared** | `aisafe.dlx` | `aisafe.dlx` |
| Dead-letter queue (DLQ): one per consumer queue, bound to `aisafe.dlx` with the queue name as routing key | `<queue>.dlq` | `flightroutes.flightroutes-2.flightroutes.dlq` |

`<instanceId>` is the replica's `aisafe.instance-id` exactly as configured (`flightroutes-2`), which is why the service name appears twice in the example. Consequences of "one queue per instance":
- **Every replica receives every matching event** (each has its own copy in its own queue). Use this when each replica keeps its own copy of the data, which is our case (each replica has its own database).
- If a replica is down, its events **wait in its queue** and are processed when it comes back.
- Queues are **not** shared by the replicas of a service (that would be the "competing consumers" pattern, where each event goes to only one replica).

Binding patterns (topic syntax): `*` = exactly one word, `#` = zero or more words. `route.*` = every route event; `route.deleted` = only deletions; `#` = everything from that exchange.

Picture for Flight Routes with 3 replicas:

```text
                               route.created / route.updated / route.deleted
flightroutes-N ──publish──▶ [aisafe.flightroutes.events] (topic)
                                 │ route.*        │ route.*        │ route.*
                                 ▼                ▼                ▼
          flightroutes.flightroutes-1.flightroutes  ...-2...   ...-3...   (one per replica)
                 │ rejected after retries
                 ▼
            [aisafe.dlx] ──▶ flightroutes.flightroutes-1.flightroutes.dlq
```

A service that wants route events (e.g. Airports) **adds its own queues** (`airports.airports-1.flightroutes`, ...) bound to the same exchange. The producer does not change.

---

## 4. How to publish an event from your service

**Step 1: dependency** (`code/<service>/pom.xml`). `common` declares AMQP as *optional*, so you must add it yourself:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Step 2: configuration** (`code/<service>/src/main/resources/application.properties`). Copy this block as is:

```properties
aisafe.messaging.enabled=${AISAFE_MESSAGING_ENABLED:true}
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USER:}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:}
spring.rabbitmq.publisher-confirm-type=simple
aisafe.messaging.max-attempts=3
aisafe.messaging.initial-interval=500ms
aisafe.messaging.multiplier=2.0
aisafe.messaging.max-interval=5s
management.health.rabbit.enabled=${aisafe.messaging.enabled}
```

With this block, the common library declares your exchange `aisafe.<spring.application.name>.events` at startup and gives you an `EventPublisher` bean.

**Step 3: keep your existing tests broker-free.** Create `src/test/resources/config/application.properties` with:

```properties
aisafe.messaging.enabled=false
```

Spring Boot loads `config/application.properties` *on top of* your main file (it does not replace it), so only this key changes in tests.

**Step 4: raise a domain event inside your transaction and publish it AFTER the commit.** Publishing inside the transaction could announce a change that is then rolled back. Flight Routes does it in two classes:

```java
// 1) In your write service (already @Transactional): raise an in-process Spring event.
//    With messaging off nobody listens to it, so this is always safe.
@Service
@RequiredArgsConstructor
public class UpdateAircraftStatusService {
    private final ApplicationEventPublisher events;
    // ...
    @Transactional
    public AircraftDTO updateStatus(String registration, String newStatus) {
        Aircraft saved = repository.save(aircraft);
        events.publishEvent(new AircraftStatusChanged(saved));   // your own small record
        return assembler.toDTO(saved);
    }
}

// 2) After the commit, turn it into an integration event.
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AircraftIntegrationEventPublisher {
    private final EventPublisher eventPublisher;   // from common

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AircraftStatusChanged change) {
        Aircraft a = change.aircraft();
        try {
            eventPublisher.publish(
                    "AircraftStatusChanged",                                  // eventType
                    MessagingNames.routingKey("aircraft", "status-changed"),  // aircraft.status-changed
                    a.getRegistrationNumber(),                                // aggregateId
                    new AircraftStatusPayload(a.getRegistrationNumber(), a.getModelName(),
                            a.getStatus().name(), a.getVersion()));          // payload (a record)
        } catch (AmqpException e) {
            log.error("Aircraft {} saved but the event could not be published: {}", a.getRegistrationNumber(), e.getMessage());
        }
    }
}
```

Reference: `flightroutes/.../command/RouteChanged.java`, `command/RouteIntegrationEventPublisher.java`, and `services/CreateFlightRouteService.java` / `UpdateFlightRouteService.java` (one `events.publishEvent(...)` line each).

Checklist:
- [ ] `eventType` in past tense (`...Created`, `...Updated`, `...Deleted`, `...StatusChanged`).
- [ ] Routing key via `MessagingNames.routingKey(aggregate, action)`.
- [ ] Payload is a Java `record` with the full state and a `version` (your entity's `@Version`), so consumers can drop stale events.
- [ ] Publish only from the replica that **owns** the entity (where the write happened), never from a replica that forwarded the command.
- [ ] Document the event (type, routing key, payload fields) in your L3 document and tell the consumers.

---

## 5. How to consume events in your service

Do steps 1–3 of section 4 first (dependency, configuration, test config).

**Step 1: subscribe this instance.** This declares your queue, its DLQ and the bindings:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
public class MessagingConfiguration {

    /** Queue airports.<instanceId>.flightroutes, bound to route.created, route.updated, route.deleted. */
    @Bean
    public EventSubscription flightRoutesEvents(MessagingTopology topology) {
        return topology.subscribe("flightroutes", "route.*");
    }
}
```

**Step 2: declare the payload you need** as a record with only the fields you read. Extra fields in the JSON are ignored:

```java
public record RoutePayload(String routeId, String originIata, String destinationIata, String status, Long version) { }
```

**Step 3: listen and apply idempotently, in one transaction.**

```java
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RouteEventsListener {
    private final RouteCountProjection projection;

    // The bean name of step 1 + ".queueName"
    @RabbitListener(queues = "#{flightRoutesEvents.queueName}")
    public void on(IntegrationEvent<RoutePayload> event) {   // the generic type is resolved from this signature
        projection.apply(event);
    }
}

@Service
public class RouteCountProjection {
    private final IdempotentEventHandler idempotent;
    // + your repositories

    public RouteCountProjection(ProcessedEventStore processedEvents /* , repositories */) {
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    @Transactional   // the change and the "already processed" record commit together
    public void apply(IntegrationEvent<RoutePayload> event) {
        idempotent.handle(event, e -> {
            switch (e.eventType()) {
                case "RouteCreated", "RouteUpdated" -> upsert(e.payload());   // insert or update by id
                case "RouteDeleted" -> delete(e.payload().routeId());
                default -> { }                                                // unknown type: ignore
            }
        });
    }
}
```

**Step 4 (recommended): remember processed events in your database.** The default `ProcessedEventStore` is in memory. With a JPA implementation the "event applied" and "event id recorded" steps share one transaction. Copy `flightroutes/.../infrastructure/messaging/ProcessedEvent.java`, `ProcessedEventRepository.java` and `JpaProcessedEventStore.java` (3 small classes). Declaring it as a `@Component` replaces the in-memory default.

Rules for handlers:
- **Return normally = success.** The message is acknowledged (ack) only after your method returns.
- **Throw = failure.** The message is retried (`aisafe.messaging.max-attempts`, default 3, with back-off) and then moved to your DLQ. Do not catch and hide exceptions you cannot handle.
- **Be idempotent anyway:** use upserts by id. Use `version` to ignore events older than what you already have (`ReplicatedRoute.isNewerThan` in Flight Routes).
- **Unknown `eventType`: ignore it** (log it). Producers may add event types.
- **Your own events:** with one queue per instance, a service that subscribes to its own exchange also receives the events it published. `event.isFrom(myInstanceId)` tells you; either skip them or apply them idempotently (Flight Routes applies them).
- Do not call other services over HTTP inside a handler. If you need their data, subscribe to their events.

Reference: `flightroutes/.../infrastructure/messaging/RouteMessagingConfiguration.java`, `query/RouteEventsListener.java`, `query/RouteProjection.java`, `query/ReplicatedRoute.java`.

---

## 6. Reliability rules

All of these are configured once in `common` (`EventMessagingAutoConfiguration`, `ConsumerRetry`, `EventSubscription`). Do not override them per service without telling the team.

| Rule | How |
|---|---|
| Messages survive a broker restart | Durable exchanges and queues; messages sent as persistent; broker data in a Docker volume |
| Messages survive a **consumer** restart | One durable, non-exclusive, non-auto-delete queue per instance: events wait there while the replica is down |
| The producer knows the broker got the event | `publisher-confirm-type=simple`: `EventPublisher` waits for the broker's confirm (`aisafe.messaging.confirm-timeout`, 5 s) and throws `AmqpException` otherwise |
| Ack only after successful processing | Acknowledge mode `AUTO`: ack when the listener method returns, reject when it throws |
| Bounded retries | `aisafe.messaging.max-attempts` (3 in total) with exponential back-off (`initial-interval` 500 ms × `multiplier` 2, up to `max-interval` 5 s), inside the consumer |
| Failed messages are kept, not lost | After the last attempt the message is rejected **without requeue**. The queue's dead-letter settings send it to `aisafe.dlx`, then to `<queue>.dlq`. Inspect it in the management UI (queue → *Get messages*) |
| Duplicates are harmless | Delivery is **at least once**: `IdempotentEventHandler` + `ProcessedEventStore` skip an `eventId` already processed. Handlers also upsert |
| Order per entity | One consumer per queue (`concurrentConsumers = 1`), and only the owner replica publishes an entity's events, so the events of one entity arrive in order. `version` in the payload is a safety net for replays |

**Known gap: no transactional outbox yet.** Events are published *after* the commit. If the broker is unreachable at that moment, the change is saved but the event is lost; the error is logged ("could not be published (event lost)") and the HTTP request still succeeds. The fix is a transactional outbox: write the event to a table in the same transaction and publish it from there. It is planned, not implemented.

---

## 7. Design decision: topic exchange (not fanout)

| | Fanout exchange | Topic exchange (chosen) |
|---|---|---|
| Routing | Copies **every** message to **every** bound queue; the routing key is ignored | Copies a message to the queues whose binding **pattern** matches its routing key (`route.*`, `route.deleted`, `#`) |
| Broadcast to all replicas | Yes | Yes. Each replica has its own queue, and `#` (or `route.*`) matches everything |
| A consumer that wants only some events | Receives everything and discards in code, or the producer needs one exchange **per event type** | Binds only the keys it needs, e.g. Airports binds `route.created` and `route.deleted` and never receives `route.updated` |
| Adding a new event type | New exchange (if split per type) or extra traffic to every consumer | New routing key on the same exchange; existing consumers are not affected |
| Cost | Slightly cheaper matching | Negligible at our volume |

**Why topic:** we need broadcast (every replica must get every event, so it can update its own database), and a fanout exchange gives that. But each service publishes several event types and each consumer needs a different subset. A topic exchange gives the same broadcast **plus** filtering in the broker, with **one exchange per service** and readable routing keys (`<aggregate>.<action>`). A direct exchange would filter only by exact key, with no wildcards (`route.*`).

---

## 8. Consistency model

- **Inside a service (replicas):**
  - The **write model** stays partitioned: an entity lives in the replica that created it (its *owner*), and commands to an existing entity are forwarded to the owner over HTTP (`PeerClient.forwardToOwner`). On the owner, writes are strongly consistent (local transaction + `@Version`).
  - **Read models** built from events (for now only Flight Routes' `REPLICATED_ROUTE`) are **eventually consistent**. After a write, the other replicas show it a few milliseconds later. If a replica is down, it catches up from its queue when it restarts. There is **no read-your-writes guarantee** across replicas: a client that writes on replica 1 and reads on replica 2 may briefly see the old value.
- **Between services:** **eventual consistency**. A consumer keeps its own copy of the data it needs (e.g. the status of an aircraft) and updates it from events. A decision taken on that copy can use slightly stale data. Where that is not acceptable (e.g. scheduling an aircraft that has just gone `UNDER_MAINTENANCE`), the owner must re-validate (rejecting the command) or a compensation event must undo it. Decide this per use case and write it in your L3 document.
- **Convergence condition:** all replicas end up with the same read model once they have processed the same events. Duplicates (idempotency), reordering (single producer per entity, `version`) and late delivery (durable queues) do not prevent it. **Lost events** (no outbox, section 6) and **queues created after the event** (a new replica's queue only gets events published after it was declared) do. A brand-new replica starts with an empty read model.

---

## 9. Flight Routes proof of concept

| Event | Routing key | Published when | Payload |
|---|---|---|---|
| `RouteCreated` | `route.created` | US110 `POST /api/routes`, after the commit, by the replica that stored the route | `RouteEventPayload`: `routeId`, `originIata`, `destinationIata`, `distance`, `minRange`, `minCapacity`, `estimatedFlightTime`, `status`, `version` |
| `RouteUpdated` | `route.updated` | US112 `PATCH /api/routes/{id}` (details or `ACTIVE`/`INACTIVE`), after the commit, by the **owner** replica (other replicas forward the PATCH to it) | same |
| `RouteDeleted` | `route.deleted` | reserved: there is no delete endpoint today. Consumers already handle it | `routeId`, `version` |

Each replica consumes `route.*` from its own queue and applies the events to its own table `REPLICATED_ROUTE`, a read model separate from the write table `FLIGHT_ROUTE`. Keeping them separate means the existing HTTP forwarding and the summed statistics (B5 `count-by-airport`) keep working unchanged: putting replicated rows in the write table would count every route once per replica.

**Compare the two mechanisms:**
- `GET /api/routes/search` (existing): the replica that receives the request asks its peers over HTTP and merges the results. It sees everything when all peers answer.
- `GET /api/routes/replicated` (new, `ATCC`): the replica answers **from its own database only**, without calling any peer. Call it on 8084, 8184 and 8284: after a create or update the three answers become identical (same `version`, same `ownerInstance`), even if a peer is down.

Demo data (`demo` profile) is loaded directly into the database at startup and is **not** published as events. Only routes created or updated through the REST API appear in `/api/routes/replicated`.

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `start-all` says "RabbitMQ credentials are not set" | `infra/.env` is missing: copy `infra/.env.example` |
| `ACCESS_REFUSED` in a replica's log | The user/password in `infra/.env` differ from the ones the broker volume was created with. Use the original ones, or erase the volume (`... down -v`) |
| `/actuator/health` shows `rabbit: DOWN` | The broker is not running or not reachable; the replica keeps serving HTTP and reconnects by itself when the broker comes back |
| Messages pile up in a queue | That replica is down, or its listener keeps failing (see its log) |
| Messages in a `.dlq` | The handler threw on every attempt. Read the message in the UI (queue → *Get messages*), fix the cause, then purge the DLQ or publish the message again |
| Old routes appear in `/api/routes/replicated` after a full restart | Databases are in-memory, but queues are durable: events published before the restart are still in the queues of replicas that were down. In development, purge the `flightroutes.*` queues in the UI before starting again, or erase the broker volume (`... down -v`) |
