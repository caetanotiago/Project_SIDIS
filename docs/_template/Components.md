# C4 Level 3 — <Service> Service: Components

<!-- TEMPLATE: copy to docs/<level>/<module>/<Module>-<Level>.md and replace <Service>, <module>, <Module>, <db>, <P1..3>. Links are relative to that destination folder. -->

> Zoom into the **<Service> Service** container of [Level 2](../../2-containers/Containers.md). Reference example: [Flight Routes — Components](../flightroutes/FlightRoutes-Components.md).
> Lower levels: [L4 Code](../../4-code/<module>/<Module>-Code.md) · [+1 Scenarios](../../5-scenarios/<module>/<Module>-Scenarios.md)

![C4-L3-<Module>-Components](svg/C4-L3-<Module>-Components.svg)

## 1. Container summary

| Property | Value |
|---|---|
| Bounded context | |
| Replicas | `<module>-1` :<P1>, `<module>-2` :<P2>, `<module>-3` :<P3> |
| Database | H2 in-memory per replica: `<db>-1/2/3` |
| Source code | [code/<module>](../../../code/<module>) |
| API | `http://localhost:<P1>/swagger-ui.html` |

## 2. Components

| Component | Technology / classes | Responsibility |
|---|---|---|
| Security & Observability filters | `common` + `<Module>AuthorizationRules` | |
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
