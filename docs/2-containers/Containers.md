# C4 Level 2 — Containers

> The separately deployable units that make up AISafe, the technology of each one and how they communicate. Zoom of the single box of [Level 1](../1-context/Context.md).

![C4-L2-Containers](svg/C4-L2-Containers.svg)

## 1. Containers

The monolith was decomposed by **bounded context** (domain-driven segregation). Each service is a separate Spring Boot application, runs as **three replicas**, and each replica owns **its own database**. Data of another service is only reachable through that service's REST API.

| Container | Technology | Replicas (port) | Data | Responsibility (bounded context) | Details |
|---|---|---|---|---|---|
| **Aircraft Management Service** | Java 21, Spring Boot 4 | 8081 / 8181 / 8281 | H2 `aircraftManagementdb-N` | *Fleet*: aircraft models, aircraft, operational status (WP#1) | [L3](../3-components/aircraftmanagement/AircraftManagement-Components.md) |
| **Maintenance Service** | Java 21, Spring Boot 4 | 8082 / 8182 / 8282 | H2 `maintenancedb-N` | *Maintenance*: templates, records, reports and due-maintenance alerts (WP#4) | [L3](../3-components/maintenance/Maintenance-Components.md) |
| **Airports Service** | Java 21, Spring Boot 4 | 8083 / 8183 / 8283 | H2 `airportsdb-N` | *Airport infrastructure*: airports, runways, certification (WP#2) | [L3](../3-components/airports/Airports-Components.md) |
| **Flight Routes Service** | Java 21, Spring Boot 4 | 8084 / 8184 / 8284 | H2 `flightRoutesdb-N` | *Flight operations*: routes, history, scheduled flights, network queries (WP#3) | [L3](../3-components/flightroutes/FlightRoutes-Components.md) |
| *common* (library, not a container) | Java library | embedded in every service | — | Security, peer forwarding, resilience, audit — the same distributed behaviour in every container | [L3](../3-components/common/Common-Components.md) |

Ports, database names and per-replica configuration: [Ports.md](Ports.md).

## 2. Communication between containers

All communication is **synchronous HTTP/REST with JSON** (commands and queries; the assignment does not allow events/message queues).

**Users** call every service's REST API (Swagger at `/swagger-ui.html`) with `Authorization: Bearer <JWT>`. Which role reaches which container is decided by each service's `<Service>AuthorizationRules`:

| Role | Aircraft Management | Airports | Flight Routes | Maintenance |
|---|---|---|---|---|
| Backoffice Operator | registers aircraft models (US101); US204 top models; reads aircraft and models | registers and updates airports, operational status, certifications (US106, US106a, US109, US208); US210 busiest airports | updates / deactivates routes (US112) | — |
| ATCC | registers aircraft (US102); status (US105); US203, US204, US206; reads aircraft and models | airport details, search and grouping (US107, US108, US211); certifications (US106a) | routes, route history, scheduled flights, network queries (US110–US114, US209, US212–US216) | statistics, cost report and alerts (US117, US220, US222) |
| Maintenance Technician / Supervisor | reads aircraft and models; status (US105) | — | — | templates, records, categorisation, ongoing records, turnaround report |

The `ADMIN` role exists in the token, but no endpoint requires it.

Calls **between** containers:

| From | To | Endpoints | Purpose |
|---|---|---|---|
| Flight Routes | Airports | B2: `GET /api/airports/{iata}` | Coordinates (route distance) and operational status (US110, US209, US212, US216) |
| Flight Routes | Aircraft Management | B1: `GET /api/aircrafts/{reg}`, `GET /api/aircraft-models/{model}` | Status, seating capacity and range (US212, US213) |
| Aircraft Management | Flight Routes | B4b: `GET /api/scheduled-flights/statistics/minutes-by-aircraft` | Flight minutes per aircraft for US204 (top models) and US206 (operational hours) |
| Aircraft Management | Flight Routes | B6: `GET /api/routes/compatible?range=&capacity=` | Active routes compatible with an aircraft (US203) |
| Airports | Flight Routes | B5: `GET /api/routes/statistics/count-by-airport` | Route counts per airport, ranked by Airports (US210 busiest airports) |
| Maintenance | Aircraft Management | B3a: `GET /api/aircrafts/{reg}` | Aircraft exists, its model and status (US115 records, US116 history) |
| Maintenance | Aircraft Management | B3b: `PATCH /api/aircrafts/{reg}/status` | `UNDER_MAINTENANCE` when a record starts (US115), `AVAILABLE` when the last ongoing record is completed (US119), compensation on rollback |
| Maintenance | Aircraft Management | B3c: `GET /api/aircraft-models/{model}` | Applicable models of a template exist (US115 templates) |
| Maintenance | Aircraft Management | B3d: `GET /api/aircrafts?page=&size=100`, `GET /api/aircrafts/operational-hours?page=&size=100` (all pages) | Fleet and flight hours for due-maintenance alerts (US222) |
| Replica | other replicas of the **same** service | the same endpoint that was called (or its non-paginated `/search` / statistics variant), with `X-Peer-Hops: 1` | Peer forwarding / aggregation (section 3.2) |

The B4b, B5 and B6 endpoints are **service-only** (section 3.5): a user calling them directly gets `401`, even with a valid JWT. Flight Routes also provides `GET /api/scheduled-flights/operational-hours` (B4a), but no other service calls it.

Maintenance is only a consumer: no other service calls it, and the aircraft status in Aircraft Management is the single source of truth for availability (Flight Routes never calls Maintenance).

Exact request/response schemas, status codes, roles and replica behaviour of each call: [Integration-Contracts.md](Integration-Contracts.md).

**Context mapping (DDD):** a caller is a *customer* of the called service (*supplier*); each caller translates the remote DTOs into its own model in an **anti-corruption layer** (`clients` package). Aggregates of other services are referenced only **by identity** (IATA code, registration number). The `common` library is a technical *shared kernel* without domain classes.

**HTTP headers used between containers**

| Header | Direction | Meaning |
|---|---|---|
| `Authorization: Bearer <JWT>` | request | End user; forwarded unchanged to peers and remote services |
| `X-Correlation-Id` | request/response | Request id, propagated to every replica/service and written in every log line |
| `X-Peer-Hops` | request | Present only in replica-to-replica calls; the receiver answers with local data only |
| `X-Service-Key` | request | Shared secret that authenticates the calling service. Sent automatically by `common` on every inter-service and peer call; **required** on peer calls and on the service-only Flight Routes endpoints |
| `X-Caller-Instance` | request | Calling replica/service (audit) |
| `X-Partial-Response`, `X-Unavailable-Peers` | response | A replica did not answer; the result may be incomplete |

## 3. Distribution design decisions

### 3.1 Instance replication and data partitioning

- **Cloning:** the same JAR runs three times with the Spring profiles `instance1`, `instance2`, `instance3` (port, instance id, database, peer list in `application-instanceN.properties`).
- **Database per replica:** no shared database. A record is stored in the replica that received the write (partitioning by write location).
- **Single owner per record:** a write to an existing record is executed by the replica that stores it; other replicas forward the command to it (unicast). Each record has exactly one version, so there are no write conflicts between replicas.
- **Peer discovery:** static peer list per replica (`aisafe.peers.urls`), full mesh, excluding the replica itself.

### 3.2 Peer-to-peer query resolution and response aggregation

When a GET reaches a replica that does not hold all the data, the replica forwards the query to its peers and aggregates the answers (implemented once, in `PeerClient` of the `common` library):

| Kind of GET | Strategy |
|---|---|
| One resource by id | Local lookup; if missing, **parallel** GET to all peers; the first `200` wins; `404` means "not mine"; nobody has it → `404` |
| Collections | Local query + the **same GET** to all peers in parallel; merge **by id** (local copy wins); paginate **after** merging |
| Statistics / counts | Each replica computes over its own data; results are **summed** (each record lives in exactly one replica) |

- **No forwarding loops:** peer calls carry `X-Peer-Hops: 1`; a peer request is answered only with local data and never forwarded again (one hop reaches every replica in a full mesh). `X-Peer-Hops > 1` is rejected.
- **Same endpoint, same authorization:** peers are called on the endpoint the client called, with the user's JWT, so they apply the same access rules.

### 3.3 Consistency model (CAP)

AISafe favours **Availability and Partition tolerance (AP)** with **eventual consistency**:

- During a failure or partition every reachable replica keeps answering reads (with the data it can reach, flagged with `X-Partial-Response: true`) and accepting writes.
- Data stored in an unreachable replica is temporarily invisible (a GET by id returns `404`, flagged as partial) and reappears as soon as the replica recovers — there is no stale copy to reconcile, because each record has a single owner.
- Cross-replica uniqueness rules (e.g. one route per origin/destination, one flight per aircraft and time slot) are checked against the local data **and the peers that answer**. Under a partition, or with truly simultaneous writes on two replicas, a duplicate can be created — the accepted price of AP.
- Inside the owner replica, optimistic locking (`@Version`) turns two **overlapping** updates of the same record into one success and one `409`. The version is not part of any update request (no `version` field, `ETag` or `If-Match`), so a client that updates from stale data is not detected: sequential updates are last-writer-wins ([Integration-Contracts §8](Integration-Contracts.md#8-known-limitations)).
- A strongly consistent alternative (single writer / quorum / consensus) would block writes during partitions and is out of scope for Assignment 1.

### 3.4 Fault tolerance

| Mechanism | Configuration (`aisafe.*`) |
|---|---|
| **Timeouts** on every remote call | `http.connect-timeout=1s`, `http.read-timeout=3s`, `http.aggregation-timeout=5s` |
| **Retry with exponential backoff** (only I/O errors, timeouts, 5xx; never 4xx) | `http.max-retries=2`, `http.initial-backoff=100ms` |
| **Circuit breaker** per target (CLOSED → OPEN after N failures → HALF_OPEN after the open time) | `circuit-breaker.failure-threshold=3`, `circuit-breaker.open-duration=10s` |
| **Load balancing + failover** to other services (round-robin over all their replicas) | `services.<service>.urls` |
| **Graceful degradation** — partial responses instead of errors | `X-Partial-Response` |
| **Health checks** — `/actuator/health` with the circuit state of each peer | public endpoint |
| `503 Service Unavailable` only when **no** replica of a remote service answers | — |

### 3.5 Security

| Concern | Implementation |
|---|---|
| **Access control (RBAC)** | Stateless JWT validated by every replica (shared `jwt.secret`), roles in the token, per-endpoint rules in each service (`<Service>AuthorizationRules`). Unauthenticated → `401`, wrong role → `403`. |
| **Inter-service authentication** | Shared secret `X-Service-Key` (`aisafe.security.service-key`, env `AISAFE_SERVICE_KEY`), compared in constant time, **sent automatically on every inter-service and peer call** by the `common` `RestClient`. It is **required** (otherwise `401`) on replica-to-replica calls (`X-Peer-Hops`) and on the **service-only** endpoints a provider lists in `aisafe.security.service-only-paths` — in Flight Routes: `count-by-airport` (B5), `compatible` (B6), `minutes-by-aircraft` (B4b), `operational-hours` (B4a) and `usage-by-route`. A wrong key is always rejected. The user's JWT and role are still checked. |
| **Encryption in transit** | Profile `tls`: HTTPS (TLS 1.3/1.2) on every replica and on every outgoing call (`aisafe.scheme=https`, truststore `aisafe.tls.trust-store`). The development certificate (CN/SAN `localhost`) is **not in git**: `code/scripts/generate-dev-certs.*` creates it in `code/common/src/main/resources/tls/`, and the jars must be rebuilt afterwards (the start scripts stop with instructions if it is missing). |
| **Audit logging** | Logger `AUDIT`: one line per request with user, roles, method, path, status, source (`client:<ip>`, `peer:<replica>`, `service:<name>`), duration and correlation id; one log file per replica (`code/logs/<service>-N.log`). |
| **Development token** | `POST /auth/dev-token` only while there is no login service; **enabled by default**, disabled with `AISAFE_DEV_TOKEN=false`. `jwt.secret` and the service key also have development defaults in `application.properties` and must be overridden (`JWT_SECRET`, `AISAFE_SERVICE_KEY`) outside development. |
| **Per-service rules** | Each `<Service>AuthorizationRules` lists only its own endpoints and must not call `anyRequest()`: `common` adds `anyRequest().authenticated()` and stops the service at start-up with a message naming the class if a rule calls it. |

## 4. Deployment

![C4-L2-Deployment](svg/C4-L2-Deployment.svg)

Local deployment: every replica is a JVM on the developer machine (12 JVMs with 3 replicas of each service), started from `code/` with:

```bash
./scripts/start-all.sh [replicas 1-3] [profiles] [jvm-options]                 # all 4 services; defaults: 3, none, -Xmx320m
.\scripts\start-all.ps1 [-Replicas 1-3] [-Profiles demo,tls] [-Memory "-Xmx320m"]   # Windows
./scripts/stop-all.sh            .\scripts\stop-all.ps1                         # stops everything start-all started
./scripts/start-replicas.sh <service> [demo][,tls]                             # the 3 replicas of one service
.\scripts\start-replicas.ps1 -Service <service> [-Profiles demo]
./scripts/run-instance.sh <service> <1|2|3> [profiles]                          # a single replica
```

`start-all` builds the jars once if they are missing (or older than the TLS certificate), starts every replica through `run-instance`, writes `code/logs/<service>-<n>.out` and `.pid` (git-ignored), waits for `/actuator/health` and prints a table of URLs and health. `stop-all` stops every replica listed in `code/logs/*.pid`, including the JVM under each wrapper. Each replica also writes its own log `code/logs/<service>-<n>.log` (including the audit lines). On a machine with little memory, use 1 replica per service.

The diagram details Flight Routes; the other services are deployed the same way (one JVM and one H2 database per replica). Ports and per-replica configuration: [Ports.md](Ports.md).
