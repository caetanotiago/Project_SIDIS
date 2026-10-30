# C4 Level 3 — Maintenance Service: Components

> Zoom into the **Maintenance Service** container of [Level 2](../../2-containers/Containers.md).
> Lower levels: [L4 Code](../../4-code/maintenance/Maintenance-Code.md) · [+1 Scenarios](../../5-scenarios/maintenance/Maintenance-Scenarios.md)

Este documento detalha os componentes internos do contentor **Maintenance**, as suas responsabilidades e as dependências entre as camadas da arquitetura.

![C4-L3-Maintenance-Components](svg/C4-L3-Maintenance-Components.svg)

## 1. Resumo do contentor

| Propriedade | Valor |
|---|---|
| Bounded context | Maintenance — planeamento e registo de intervenções de manutenção de aeronaves |
| Réplicas | `maintenance-1` :8082, `maintenance-2` :8182, `maintenance-3` :8282 |
| Base de dados | H2 em memória por réplica: `maintenancedb-1/2/3` |
| Código-fonte | [code/maintenance](../../../code/maintenance) |
| API | `http://localhost:8082/swagger-ui.html` |

## 2. Componentes por camada DDD

| Camada | Componente | Responsabilidade |
|---|---|---|
| **Interfaces / Controllers** | `MaintenanceController` | Expõe os endpoints REST para agendar uma manutenção, registar a conclusão de uma intervenção e consultar o histórico por matrícula. Recebe os pedidos e delega a execução na camada de aplicação. |
| **Application / Services** | `MaintenanceRecordService` | Orquestra os casos de uso, aplica as validações de aplicação, coordena o acesso ao repositório e solicita a validação/atualização da aeronave ao serviço externo. Implementa a compensação do estado da aeronave se a gravação local do início da manutenção falhar. |
| **Infrastructure / Repositories** | `MaintenanceRepository` | Interface Spring Data JPA para persistir, recuperar e consultar aggregates `Maintenance`. |
| **Domain** | `Maintenance` | Aggregate Root que representa uma intervenção e protege as invariantes do seu ciclo de vida, como transições de estado e dados de agendamento/conclusão. |
| **Domain** | `MaintenanceStatus` | Enum que identifica o estado da intervenção, por exemplo `SCHEDULED`, `IN_PROGRESS`, `COMPLETED` e `CANCELLED`. As transições permitidas são controladas pelo domínio. |
| **Domain** | `MaintenanceReport` (Value Object, quando aplicável) | Agrupa os dados imutáveis do relatório de uma intervenção, como a descrição dos trabalhos executados e a data de conclusão. |
| **Infrastructure / Integração** | `AircraftClient` | Adapta as chamadas HTTP a Aircraft Management para obter os dados da aeronave e atualizar o estado para `UNDER_MAINTENANCE` ou `AVAILABLE`, evitando acoplar o domínio ao formato da API externa. |

## 3. Dependências externas

O serviço **Maintenance** depende do serviço **Aircraft Management** para confirmar que a matrícula associada a um agendamento ou a uma intervenção corresponde a uma aeronave existente. Esta validação evita a criação de registos de manutenção para aeronaves desconhecidas.

A comunicação entre microsserviços é síncrona e feita via **HTTP/REST**, com payloads JSON. A camada de integração encapsula a chamada remota e traduz a resposta para que a camada de aplicação possa prosseguir ou rejeitar o caso de uso de forma explícita. Os endpoints e contratos detalhados devem ser definidos em [Integration-Contracts.md](../../2-containers/Integration-Contracts.md).
