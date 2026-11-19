# AISafe — Architecture Documentation (C4+1)

The architecture is documented with the **C4 model** (Simon Brown) plus a fifth view of **scenarios** (C4+1). Each level zooms into the previous one and is written for a different audience.

| Level | Question it answers | Audience | Document |
|---|---|---|---|
| **L1 — Context** | How does AISafe fit into the world? Who uses it? | everyone (business, managers, team) | [1-context/Context.md](1-context/Context.md) |
| **L2 — Containers** | Which separately deployable units make up AISafe, and how do they communicate? Replication, consistency, fault tolerance, security, deployment. | engineers, project manager | [2-containers/Containers.md](2-containers/Containers.md) · [Ports.md](2-containers/Ports.md) · [Integration-Contracts.md](2-containers/Integration-Contracts.md) (REST contracts; section 9: RabbitMQ event contracts and how to publish/consume) · [Unicorns-Mock.md](2-containers/Unicorns-Mock.md) (`/api/v2/unicorns` mock and CQRS) |
| **L3 — Components** | What are the main building blocks inside each container? | developers of that service | [3-components/](3-components/) |
| **L4 — Code** | Which classes implement the components (domain model, class diagrams, patterns)? | developers | [4-code/](4-code/) |
| **+1 — Scenarios** | How do the elements work together in each use case (user stories, sequence diagrams, tests)? | everyone; validates L1–L4 | [5-scenarios/](5-scenarios/) |

## Contents per container

| Container | Owner | Status | L3 Components | L4 Code | +1 Scenarios |
|---|---|---|---|---|---|
| Aircraft Management (WP#1) | Tiago Moreira | — | [Components](3-components/aircraftmanagement/AircraftManagement-Components.md) | [Code](4-code/aircraftmanagement/AircraftManagement-Code.md) | [Scenarios](5-scenarios/aircraftmanagement/AircraftManagement-Scenarios.md) |
| **Maintenance (WP#4)** | Diogo Fernandes | complete | [Components](3-components/maintenance/Maintenance-Components.md) | [Code](4-code/maintenance/Maintenance-Code.md) | [Scenarios](5-scenarios/maintenance/Maintenance-Scenarios.md) |
| **Airports (WP#2)** | Afonso Ribeiro | complete | [Components](3-components/airports/Airports-Components.md) | [Code](4-code/airports/Airports-Code.md) | [Scenarios](5-scenarios/airports/Airports-Scenarios.md) |
| **Flight Routes (WP#3)** | Tiago Caetano | complete | [Components](3-components/flightroutes/FlightRoutes-Components.md) | [Code](4-code/flightroutes/FlightRoutes-Code.md) | [Scenarios](5-scenarios/flightroutes/FlightRoutes-Scenarios.md) |
| *common library* (shared) | Tiago Caetano | — | [Components](3-components/common/Common-Components.md) | [Code](4-code/common/Common-Code.md) | (exercised by every scenario) |

L1 and L2 describe the **whole system** and are shared by the team; L3, L4 and +1 are written **per container** by its owner. Flight Routes is the reference example.

## How to build and run

Requirements: **JDK 21** and, for Assignment 2 messaging, **Docker** (Docker Desktop on Windows/macOS) for RabbitMQ. Maven is not needed: the wrapper `./mvnw` (`mvnw.cmd` on Windows) is in `code/`. All commands below run from `code/` unless stated otherwise.

**1. Once: broker credentials.** From the repository root, copy `infra/.env.example` to `infra/.env` and replace the placeholder user and password. `infra/.env` is git-ignored; never commit it. There is no `guest/guest` account. `start-all.*`, `start-replicas.*` and `run-instance.*` all load `infra/.env` (variables already set in the environment win), so every replica gets `RABBITMQ_*`.

| Variable (`infra/.env` or environment) | Default | Used by |
|---|---|---|
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | none (required) | broker account, `spring.rabbitmq.username/password` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` | `spring.rabbitmq.host/port`, scripts |
| `AISAFE_MESSAGING_ENABLED` | `true` (Flight Routes) | `aisafe.messaging.enabled` |
| `AISAFE_EXTERNAL_EVENTS` | `false` | Flight Routes `aisafe.flightroutes.external-events.enabled` ([Integration-Contracts §9.4](2-containers/Integration-Contracts.md#94-events-expected-from-the-other-services)) |

**2. Build and test.**

```bash
./mvnw -B clean verify                # build the 4 services and common + all tests
```

`RouteEventsBrokerTest` runs only when RabbitMQ is reachable with the credentials of `infra/.env` (otherwise it is skipped); every other test needs no broker.

**3. Start.**

```bash
./scripts/start-all.sh [replicas] [profiles] [jvm-options]      # e.g. ./scripts/start-all.sh 3 demo
.\scripts\start-all.ps1 [-Replicas 1-3] [-Profiles demo,tls] [-Memory "-Xmx320m"]
./scripts/stop-all.sh [--broker]   # or .\scripts\stop-all.ps1 [-Broker]
# RabbitMQ by hand (repository root):
docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env up -d --wait
```

- **RabbitMQ:** you do not have to start it by hand: `start-all` checks that the broker is reachable and runs the `docker compose` command above if it is not. If Docker is missing, `infra/.env` is missing or the broker does not come up, it stops with a message saying what to do; it never hangs. `stop-all --broker` / `-Broker` stops the replicas and the broker; without the flag the broker keeps running. Broker data is kept in the Docker volume `aisafe_rabbitmq-data`; `docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env down -v` erases it.
- **Management UI:** <http://localhost:15672>, log in with `RABBITMQ_USER` / `RABBITMQ_PASSWORD` from `infra/.env`. The **Exchanges** and **Queues** tabs show the exchanges `aisafe.<service>.events`, the queues `<instanceId>.<sourceService>` and their `.dlq` ([Integration-Contracts §9.2](2-containers/Integration-Contracts.md#92-exchanges-routing-keys-and-queues)); a queue's *Get messages* shows its events.
- **Running without a broker:** set `AISAFE_MESSAGING_ENABLED=false` before `start-all` (or `java -jar`). The service then neither publishes nor consumes events; Flight Routes still answers from its own read models, which then contain only the writes made on that replica. Event contracts: [Integration-Contracts §9](2-containers/Integration-Contracts.md#9-assignment-2--event-contracts); reliability and known limitations: [Containers §3.4–3.6](2-containers/Containers.md#34-fault-tolerance).
- **`start-all`** starts every service with `replicas` (1–3, default 3) replicas, extra Spring `profiles` (default none) and JVM options (default `-Xmx320m`). It builds the jars first if they are missing, writes logs and PID files to `code/logs/`, waits for **`/actuator/health/liveness`** and prints the URL of each replica (`/actuator/health/readiness` also includes RabbitMQ). **`stop-all`** stops them. 12 JVMs need several GB of RAM; use 1 or 2 replicas per service on a small machine. One service only: `scripts/start-replicas.* <service> [profiles] [replicas]`; one replica: `scripts/run-instance.*`.
- **Databases:** one per replica. Flight Routes uses H2 **files** in `code/data/` (git-ignored; delete the folder, and purge the queues, to start again from the demo data); the other services use in-memory H2.
- **Profiles:** `demo` loads demonstration data (different in each replica) in Airports, Flight Routes and Maintenance; Aircraft Management always loads its demo fleet. Flight Routes inserts it only once and publishes it as events. `tls` switches every replica and every inter-service call to HTTPS. Before the first `tls` run, create the development certificate with `./scripts/generate-dev-certs.sh` (or `.\scripts\generate-dev-certs.ps1`) and rebuild; it is not stored in git.
- **Tokens:** `POST /auth/dev-token` on any replica with `{"username": "atcc1", "roles": ["ATCC"]}` returns a JWT for Postman or Swagger (`/swagger-ui.html`).
- **Postman:** one collection and environment per service in `5-scenarios/<container>/postman/` (`AircraftManagement`, `Airports`, `FlightRoutes`, `Maintenance`), plus the **`/api/v2/unicorns` mock** in `5-scenarios/unicorns/postman/` ([Unicorns-Mock.md](2-containers/Unicorns-Mock.md): how to create the Postman mock server). Run them in the Postman app (Collection Runner) or with `npx newman run <collection>.json -e <environment>.json`; each collection's description lists the services and profiles it needs. FlightRoutes folder 9 measures eventual consistency across the replicas; folders 6 and 10 need replicas stopped by hand first.
- **Full document:** `Documentation.md` contains all of this documentation in one file (for PDF export). Regenerate it after any change with `powershell -ExecutionPolicy Bypass -File docs\tools\build-full-doc.ps1` or `bash docs/tools/build-full-doc.sh` (from the repository root).

**Troubleshooting (messaging).**

| Symptom | Cause / fix |
|---|---|
| `start-all` says "RabbitMQ credentials are not set" | `infra/.env` is missing: copy `infra/.env.example` |
| `ACCESS_REFUSED` in a replica's log | The user/password in `infra/.env` differ from the ones the broker volume was created with. Use the original ones, or erase the volume (`... down -v`) |
| `/actuator/health/readiness` shows `rabbit: DOWN` | The broker is not running or not reachable; the replica stays live, keeps serving HTTP (reads from its read models, writes without propagation) and reconnects by itself when the broker comes back |
| A replica does not show a route created on another one | Its event has not arrived yet (look at its queue), was lost (the producer's log says "could not be published"), or the replica's database was erased after the event ([Containers §3.6](2-containers/Containers.md#36-known-limitations-of-messaging)) |
| Messages pile up in a queue | That replica is down, or its listener keeps failing (see its log) |
| Messages in a `.dlq` | The handler threw on every attempt. Read the message in the UI (queue → *Get messages*), fix the cause, then purge the DLQ or publish the message again |
| Old data after "starting from scratch" | The file databases are in `code/data/` and the queues are durable. Delete `code/data/` **and** purge the `*.flightroutes` queues (or `... down -v`) to really start from the demo data |

## Folder layout and conventions

```text
docs/
├── README.md                     this index
├── Documentation.md              generated: all the documentation in one file (docs/tools/)
├── 1-context/                    L1 — Context.md + puml/ + svg/
├── 2-containers/                 L2 — Containers.md, Ports.md, Integration-Contracts.md, Unicorns-Mock.md + puml/ + svg/
├── 3-components/<container>/     L3 — <Container>-Components.md + puml/ + svg/
├── 4-code/<container>/           L4 — <Container>-Code.md + puml/ + svg/
├── 5-scenarios/<container>/      +1 — <Container>-Scenarios.md + puml/ + svg/ + postman/
├── 5-scenarios/unicorns/postman/ the /api/v2/unicorns mock collection + environment
├── tools/                        build-full-doc.ps1 / .sh (generate Documentation.md)
└── _template/                    templates for L3, L4 and +1
```

- **Notation.** L1–L3 use the [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML) library (`!include <C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>`, `<C4/C4_Deployment>`), so persons, systems, containers and components look the same in every diagram. L4 and +1 use UML (class and sequence diagrams).
- **Diagrams as code.** PlantUML sources in `puml/`, rendered SVGs in `svg/`, both committed. To render (from the folder of the level): `java -jar plantuml.jar -tsvg -o ../svg puml/*.puml`. Sequence diagrams and the L4 class/domain diagrams (`!pragma layout smetana`) need no Graphviz. The C4 diagrams (L1–L3 and deployment) are laid out with Graphviz `dot`: the PlantUML jar bundles it on Windows (`java -jar plantuml.jar -testdot` shows the version in use); on Linux/macOS install Graphviz. The VS Code *PlantUML* extension previews them.
- **Keep diagrams in sync with the code.** L3 components and L4 classes use the real class/package names and link to the source files; update them in the same commit as the code.
- **Language:** English for documentation, consistent naming across levels.
