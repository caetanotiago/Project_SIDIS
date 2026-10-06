# C4 Level 3 — Airports Service: Components

> Zoom into the **Airports Service** container of [Level 2](../../2-containers/Containers.md). Template — follow [Flight Routes — Components](../flightroutes/FlightRoutes-Components.md).
> Lower levels: [L4 Code](../../4-code/airports/Airports-Code.md) · [+1 Scenarios](../../5-scenarios/airports/Airports-Scenarios.md)

![C4-L3-Airports-Components](svg/C4-L3-Airports-Components.svg)

## 1. Container summary

| Property | Value |
|---|---|
| Bounded context | |
| Replicas | `airports-1` :8083, `airports-2` :8183, `airports-3` :8283 |
| Database | H2 in-memory per replica: `airportsdb-1/2/3` |
| Source code | [code/airports](../../../code/airports) |
| API | `http://localhost:8083/swagger-ui.html` |

## 2. Components

| Component | Technology / classes | Responsibility |
|---|---|---|
| Security & Observability filters | `common` + `AirportsAuthorizationRules` | |
| Controllers | Spring REST Controllers | |
| Application services | Spring Services | |
| Replica queries | uses `PeerClient` | local data + peers |
| Domain model | aggregates | |
| Repositories | Spring Data JPA | local data |
| Anti-Corruption Layer | clients of other services (`ReplicatedServiceClient`) | |

## 3. Provided interfaces (REST API)

| Method | Endpoint | US | Input | Output | Roles |
|---|---|---|---|---|---|
| | | | | | |

## 4. Required interfaces

| Component | Calls | Endpoint |
|---|---|---|
| | | |

## 5. How the components realise the distribution requirements

| Requirement | Components involved |
|---|---|
| Peer forwarding of GET by id | |
| Aggregation of collections / statistics | |
| Single owner per record (updates) | |
| Calls to other services with failover | |
