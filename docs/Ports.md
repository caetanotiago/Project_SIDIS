# Port Allocation

## Services

| Service | Port | Owner | H2 database |
|---|---|---|---|
| Aircraft Management | 8081 | TBD | `aircraftManagementdb` |
| Maintenance | 8082 | TBD | `maintenancedb` |
| Airports | 8083 | TBD | `airportsdb` |
| Flight Routes | 8084 | TBD | `flightRoutesdb` |

## Replicas

Last digit identifies the service, hundreds digit identifies the replica.

| Service | Instance 1 | Replica 2 | Replica 3 |
|---|---|---|---|
| Aircraft Management | 8081 | 8181 | 8281 |
| Maintenance | 8082 | 8182 | 8282 |
| Airports | 8083 | 8183 | 8283 |
| Flight Routes | 8084 | 8184 | 8284 |

## Configuration

In each service's `src/main/resources/application.properties`:

```properties
server.port=8081
spring.datasource.url=jdbc:h2:mem:aircraftManagementdb
```

Spring Boot defaults to 8080. Without these lines, only the first service to start
will boot; the rest fail with `Port 8080 was already in use`.