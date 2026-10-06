# Port Allocation (C4 Level 2 — deployment configuration)

The last digit identifies the service, the hundreds digit identifies the replica.

| Service | Module | WP | Replica 1 (`instance1`) | Replica 2 (`instance2`) | Replica 3 (`instance3`) | H2 database (one per replica) |
|---|---|---|---|---|---|---|
| Aircraft Management | `aircraftmanagement` | WP#1 | 8081 | 8181 | 8281 | `aircraftManagementdb-N` |
| Maintenance | `maintenance` | WP#4 | 8082 | 8182 | 8282 | `maintenancedb-N` |
| Airports | `airports` | WP#2 | 8083 | 8183 | 8283 | `airportsdb-N` |
| Flight Routes | `flightroutes` | WP#3 | 8084 | 8184 | 8284 | `flightRoutesdb-N` |

## Configuration

Each module has, in `src/main/resources/`:

| File | Content |
|---|---|
| `application.properties` | Shared configuration; defaults = replica 1 |
| `application-instance1/2/3.properties` | `server.port`, `aisafe.instance-id`, `spring.datasource.url` and `aisafe.peers.urls` (the **other** two replicas) of each replica |
| `application-tls.properties` | HTTPS (`aisafe.scheme=https`, server keystore, client truststore) |
| `application-demo.properties` | Optional demo data (see `flightroutes`) |

```properties
# application-instance2.properties of airports
server.port=8183
aisafe.instance-id=airports-2
spring.datasource.url=jdbc:h2:mem:airportsdb-2;DB_CLOSE_DELAY=-1
aisafe.peers.urls=${aisafe.scheme}://localhost:8083,${aisafe.scheme}://localhost:8283
```

Calls to **other** services list all their replicas (round-robin + failover):

```properties
services.airports.urls=${aisafe.scheme}://localhost:8083,${aisafe.scheme}://localhost:8183,${aisafe.scheme}://localhost:8283
```

Start a replica with `--spring.profiles.active=instanceN` (or `scripts/run-instance.*`). Without a profile a service starts as replica 1.
Spring Boot defaults to port 8080: without these properties only the first service to start would boot (`Port 8080 was already in use`).
