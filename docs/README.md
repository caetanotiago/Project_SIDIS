# AISafe — Architecture Documentation (C4+1)

The architecture is documented with the **C4 model** (Simon Brown) plus a fifth view of **scenarios** (C4+1). Each level zooms into the previous one and is written for a different audience.

| Level | Question it answers | Audience | Document |
|---|---|---|---|
| **L1 — Context** | How does AISafe fit into the world? Who uses it? | everyone (business, managers, team) | [1-context/Context.md](1-context/Context.md) |
| **L2 — Containers** | Which separately deployable units make up AISafe, and how do they communicate? Replication, consistency, fault tolerance, security, deployment. | engineers, project manager | [2-containers/Containers.md](2-containers/Containers.md) · [Ports.md](2-containers/Ports.md) · [Integration-Contracts.md](2-containers/Integration-Contracts.md) |
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

Requirements: **JDK 21**. Maven is not needed: the wrapper `./mvnw` (`mvnw.cmd` on Windows) is in `code/`. All commands below run from `code/`.

```bash
./mvnw -q -DskipTests package         # build the 4 services and common
./mvnw -q test                        # unit and multi-replica tests

./scripts/start-all.sh [replicas] [profiles] [jvm-options]      # e.g. ./scripts/start-all.sh 3 demo
.\scripts\start-all.ps1 [-Replicas 1-3] [-Profiles demo,tls] [-Memory "-Xmx320m"]
./scripts/stop-all.sh              # or .\scripts\stop-all.ps1
```

- **`start-all`** starts every service with `replicas` (1–3, default 3) replicas, extra Spring `profiles` (default none) and JVM options (default `-Xmx320m`). It builds the jars first if they are missing, writes logs and PID files to `code/logs/`, waits for `/actuator/health` and prints the URL of each replica. **`stop-all`** stops them. 12 JVMs need several GB of RAM; use 1 replica per service on a small machine. One service only: `scripts/start-replicas.*`; one replica: `scripts/run-instance.*`.
- **Profiles:** `demo` loads demonstration data (different in each replica) in Airports, Flight Routes and Maintenance; Aircraft Management always loads its demo fleet. `tls` switches every replica and every inter-service call to HTTPS. Before the first `tls` run, create the development certificate with `./scripts/generate-dev-certs.sh` (or `.\scripts\generate-dev-certs.ps1`) and rebuild; it is not stored in git.
- **Tokens:** `POST /auth/dev-token` on any replica with `{"username": "atcc1", "roles": ["ATCC"]}` returns a JWT for Postman or Swagger (`/swagger-ui.html`).
- **Postman:** one collection and environment per service in `5-scenarios/<container>/postman/` (`AircraftManagement`, `Airports`, `FlightRoutes`, `Maintenance`). Run them in the Postman app or with `npx newman run <collection>.json -e <environment>.json`; each collection's description lists the services and profiles it needs, and the "replica failure" folder needs replica 2 stopped by hand first.
- **Full document:** `Documentation.md` contains all of this documentation in one file (for PDF export). Regenerate it after any change with `powershell -ExecutionPolicy Bypass -File docs\tools\build-full-doc.ps1` or `bash docs/tools/build-full-doc.sh` (from the repository root).

## Folder layout and conventions

```text
docs/
├── README.md                     this index
├── Documentation.md              generated: all the documentation in one file (docs/tools/)
├── 1-context/                    L1 — Context.md + puml/ + svg/
├── 2-containers/                 L2 — Containers.md, Ports.md, Integration-Contracts.md + puml/ + svg/
├── 3-components/<container>/     L3 — <Container>-Components.md + puml/ + svg/
├── 4-code/<container>/           L4 — <Container>-Code.md + puml/ + svg/
├── 5-scenarios/<container>/      +1 — <Container>-Scenarios.md + puml/ + svg/ + postman/
├── tools/                        build-full-doc.ps1 / .sh (generate Documentation.md)
└── _template/                    templates for L3, L4 and +1
```

- **Notation.** L1–L3 use the [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML) library (`!include <C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>`, `<C4/C4_Deployment>`), so persons, systems, containers and components look the same in every diagram. L4 and +1 use UML (class and sequence diagrams).
- **Diagrams as code.** PlantUML sources in `puml/`, rendered SVGs in `svg/`, both committed. To render (from the folder of the level): `java -jar plantuml.jar -tsvg -o ../svg puml/*.puml`. Sequence diagrams and the L4 class/domain diagrams (`!pragma layout smetana`) need no Graphviz. The C4 diagrams (L1–L3 and deployment) are laid out with Graphviz `dot`: the PlantUML jar bundles it on Windows (`java -jar plantuml.jar -testdot` shows the version in use); on Linux/macOS install Graphviz. The VS Code *PlantUML* extension previews them.
- **Keep diagrams in sync with the code.** L3 components and L4 classes use the real class/package names and link to the source files; update them in the same commit as the code.
- **Language:** English for documentation, consistent naming across levels.
