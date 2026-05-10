# Relatório Técnico — Workflow Platform

**Unidade Curricular:** Projeto e Seminário  
**Data:** Abril de 2026  
**Tecnologia Principal:** Kotlin · Spring Boot 3.2.3 · Spring Security · Spring Data JPA · PostgreSQL 15 · React + TypeScript

---

## Resumo

A Workflow Platform é uma API REST desenvolvida em Kotlin com Spring Boot 3.2.3, concebida para permitir a criação, gestão e monitorização de pipelines compostas por tarefas ordenadas. O sistema implementa uma arquitetura em três camadas — Controller, Service, Repository — com persistência gerida pelo Spring Data JPA sobre PostgreSQL 15, autenticação *stateless* baseada em cookie HttpOnly com tokens opacos hashed via SHA-256, e controlo de acesso baseado em papéis (RBAC) de dois níveis. O modelo de domínio centra-se na entidade `WorkflowTaskOrder`, uma tabela de associação enriquecida que suporta paralelismo, sequenciamento e políticas de *retry* por tarefa. O frontend é uma Single Page Application desenvolvida em React com TypeScript. Este relatório documenta as decisões arquiteturais, as tecnologias escolhidas e as suas justificações, o modelo de segurança do Spring Security, e a lógica de execução de workflows com suporte a trigger manual, CRON e evento.

**Palavras-chave:** Orquestração de processos; Pipeline; REST API; Spring Boot 3; Kotlin; RBAC; JPA; PostgreSQL; React; TypeScript.

---

## Abstract

The Workflow Platform is a REST API developed in Kotlin with Spring Boot 3.2.3, designed to enable the creation, management, and monitoring of pipelines composed of ordered tasks. The system implements a three-layer architecture — Controller, Service, Repository — with persistence managed by Spring Data JPA over PostgreSQL 15, stateless authentication based on HttpOnly cookies with opaque tokens hashed via SHA-256, and a two-level Role-Based Access Control (RBAC) model. The domain model centres on the `WorkflowTaskOrder` entity, a rich association table that supports parallelism, sequential ordering, and per-task retry policies. The frontend is a Single Page Application developed in React with TypeScript. This report documents the architectural decisions made, the chosen technologies and their justifications, the Spring Security pipeline, and the workflow execution logic with support for manual, CRON, and event triggers.

**Keywords:** Process orchestration; Pipeline; REST API; Spring Boot 3; Kotlin; RBAC; JPA; PostgreSQL; React; TypeScript.

---

## Lista de Acrónimos

| Acrónimo | Significado |
|---|---|
| AOP | Aspect-Oriented Programming |
| API | Application Programming Interface |
| CRUD | Create, Read, Update, Delete |
| DTO | Data Transfer Object |
| HTTP | HyperText Transfer Protocol |
| HTTPS | HyperText Transfer Protocol Secure |
| JPA | Jakarta Persistence API |
| JSON | JavaScript Object Notation |
| JWT | JSON Web Token |
| LEIC | Licenciatura em Engenharia Informática e de Computadores |
| ORM | Object-Relational Mapping |
| RBAC | Role-Based Access Control |
| REST | Representational State Transfer |
| RFC | Request for Comments |
| SPA | Single Page Application |
| SpEL | Spring Expression Language |
| SQL | Structured Query Language |
| SRP | Single Responsibility Principle |
| SSE | Server-Sent Events |
| URI | Uniform Resource Identifier |
| UUID | Universally Unique Identifier |

---

## Índice

1. [Introdução](#1-introdução)
2. [Decisões Arquiteturais](#2-decisões-arquiteturais)
3. [Estrutura do Projeto](#3-estrutura-do-projeto)
4. [Camada de Persistência — JPA e Hibernate](#4-camada-de-persistência--jpa-e-hibernate)
5. [Modelo de Domínio em Detalhe](#5-modelo-de-domínio-em-detalhe)
6. [Segurança e RBAC](#6-segurança-e-rbac)
7. [Lógica de Negócio — Camada de Serviço](#7-lógica-de-negócio--camada-de-serviço)
8. [Camada de Apresentação — Controllers e DTOs](#8-camada-de-apresentação--controllers-e-dtos)
9. [Frontend — React e TypeScript](#9-frontend--react-e-typescript)
10. [Dificuldades e Aprendizagens](#10-dificuldades-e-aprendizagens)
11. [Infraestrutura e Implantação](#11-infraestrutura-e-implantação)
12. [Análise Comparativa com Projetos de Semestres Anteriores](#12-análise-comparativa-com-projetos-de-semestres-anteriores)
13. [Conclusão](#13-conclusão)

---

## 1. Introdução

A **Workflow Platform** é uma REST API desenvolvida em Kotlin com Spring Boot 3 que permite a criação, gestão e monitorização de pipelines compostas por tarefas ordenadas. O sistema suporta controlo de acesso baseado em roles (RBAC), histórico de execuções detalhado, políticas de retry automáticas e agendamento de pipelines via expressões CRON.

O projeto foi desenvolvido no contexto de Projeto e Seminário (LEIC51N), com o objetivo de produzir uma plataforma de orquestração de processos que possa ser utilizada como infraestrutura interna de automatização. O frontend é uma Single Page Application (SPA) desenvolvida em React com TypeScript, que consome a API através de pedidos HTTP autenticados por cookie HttpOnly.

### Stack Tecnológica

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem | Kotlin | JVM 25 |
| Framework Web | Spring Boot | 3.2.3 |
| Segurança | Spring Security | 6.x (incluído no Boot 3.2) |
| ORM | Spring Data JPA + Hibernate | 6.x |
| Base de Dados | PostgreSQL | 15 |
| Build | Gradle Kotlin DSL | 8.x |
| Frontend | React + TypeScript + Vite | React 18 |
| Testes | JUnit 5 + Spring Boot Test | — |
| Infraestrutura Local | Docker / docker-compose | — |

### 1.2 Estado da Arte

A orquestração de workflows e pipelines é um domínio com múltiplas soluções disponíveis no mercado. Analisaram-se as alternativas mais relevantes antes de optar pelo desenvolvimento de uma plataforma própria:

**i) Apache Airflow** (Python, open-source): orientado para DAGs (*Directed Acyclic Graphs*), com scheduler próprio, workers distribuídos e interface web. É a solução de referência para pipelines de dados, mas requer infraestrutura pesada e é Python-centric, não se integrando naturalmente com um stack JVM.

**ii) Prefect / Temporal.io**: alternativas modernas ao Airflow, mais developer-friendly, mas orientadas para equipas de engenharia de dados com SDKs proprietários. A curva de adopção seria elevada e não justificada para um contexto de automação interna num stack JVM.

**iii) GitHub Actions / Jenkins**: focados em CI/CD de repositórios de código, não em orquestração de processos de negócio. A execução de workflows está acoplada ao ciclo de desenvolvimento, tornando-os inadequados para automação de processos internos independentes do código.

**iv) n8n / Zapier / Make**: plataformas low-code de automação de integrações. Oferecem ligação rápida entre serviços externos mas com controlo programático limitado, sem RBAC granular, sem persistência de domínio própria e com dependência de SaaS externos.

**v) Microsoft Power Automate**: solução enterprise do ecossistema Microsoft, fechada, com custo de licenciamento e sem possibilidade de deployment autónomo. Não adequada para infraestrutura própria.

A ausência de uma solução que combine num único sistema — RBAC granular com permissões atómicas por recurso e ação, execução paralela por stage, persistência JPA tipada, e interface web moderna sem dependência de SaaS externos — justificou o desenvolvimento de uma plataforma própria sobre Spring Boot 3 e PostgreSQL 15.

### 1.3 Organização do Documento

O presente relatório está organizado da seguinte forma: a Secção 2 documenta as decisões arquiteturais e justifica as tecnologias escolhidas; a Secção 3 descreve a estrutura de ficheiros do projeto; a Secção 4 detalha a camada de persistência JPA e o processo de mapeamento objeto-relacional; a Secção 5 analisa o modelo de domínio com especial atenção à entidade `WorkflowTaskOrder`; a Secção 6 explica a pipeline de segurança do Spring Security e o modelo RBAC; a Secção 7 documenta a lógica de negócio da camada de serviço; a Secção 8 descreve os controllers REST e os DTOs; a Secção 9 cobre o frontend React + TypeScript; a Secção 10 regista as dificuldades e aprendizagens; a Secção 11 descreve a infraestrutura e implantação local; a Secção 12 apresenta uma análise comparativa com projetos de semestres anteriores; e a Secção 13 conclui com reflexões sobre escalabilidade e trabalho futuro.

---

## 2. Decisões Arquiteturais

### 2.1 Kotlin em vez de Java

A escolha de Kotlin justifica-se pela expressividade da linguagem no contexto de uma API Spring Boot. As `data class` eliminam código boilerplate de DTOs (getters, setters, `equals`, `hashCode`, `toString`), as null-safety nativas reduzem a necessidade de `Optional<T>` e as funções de extensão permitem utilitários de domínio sem herança. A interoperabilidade total com o ecossistema Java significa que nenhuma biblioteca Spring é sacrificada.

Em particular, o uso de `sealed class` para o tipo `Either<Failure, Success>` (padrão *Railway-Oriented Programming*) garante que os serviços comunicam erros de domínio sem recorrer a exceções de fluxo de controlo, tornando o código mais previsível e testável em tempo de compilação.

### 2.2 Spring Boot 3.x e JVM 25

O Spring Boot 3.x exige Java 17+ como baseline, o que permite o uso de Records, Sealed Classes e Pattern Matching. A execução na JVM 25 garante acesso às mais recentes otimizações do Garbage Collector (ZGC generational) e ao Project Loom (Virtual Threads), relevante para o modelo de execução concorrente de tarefas.

A escolha do Spring Boot face a alternativas como Ktor (framework Kotlin-nativo) ou Micronaut justifica-se pela maturidade do ecossistema, suporte nativo a Spring Data JPA, Spring Security e pela vasta documentação disponível — nomeadamente a [documentação oficial do Spring Security](https://docs.spring.io/spring-security/reference/index.html) e o [Spring Data JPA Reference Guide](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/), que serviram de referência central durante a implementação.

### 2.3 Arquitetura em Camadas

O projeto segue estritamente uma arquitetura em três camadas:

```
Controller  →  Service  →  Repository
    ↕               ↕             ↕
   DTOs          Entities       JPA/SQL
```

- **Controllers** recebem pedidos HTTP, delegam para Services e nunca contêm lógica de negócio.
- **Services** encapsulam toda a lógica de domínio, anotados com `@Transactional` onde relevante, e lançam erros de domínio tipados.
- **Repositories** são interfaces Spring Data JPA que geram implementações SQL automaticamente em runtime via proxy dinâmico.

Este padrão respeita o **Princípio da Responsabilidade Única (SRP)** — cada classe tem uma única razão para mudar — e o **Princípio da Inversão de Dependência** — os serviços dependem de abstrações (interfaces de repositório), não de implementações concretas.

### 2.4 ORM (JPA/Hibernate) vs SQL Mapper (JDBI/jOOQ)

| Critério | JPA + Hibernate | JDBI / jOOQ |
|---|---|---|
| Produtividade inicial | Alta — schema gerado automaticamente | Baixa — SQL escrito manualmente |
| Controlo sobre SQL emitido | Baixo (JPQL/HQL gerado pelo ORM) | Total |
| Gestão de relações | Automática (cascades, lazy loading) | Manual |
| Risco de N+1 queries | Presente sem `FetchType.LAZY` | Inexistente |
| Type safety em queries | Moderada (JPQL é string) | Alta (jOOQ gera classes tipadas) |
| Curva de aprendizagem | Moderada | Elevada |

**Decisão tomada:** JPA + Hibernate. Para o contexto deste projeto, onde o schema é estável e as relações entre entidades são bem definidas, o JPA oferece produtividade superior sem comprometer a integridade dos dados. O `ddl-auto=update` do Hibernate gere a evolução do schema em desenvolvimento, enquanto o ficheiro `init_schema.sql` serve como referência canónica.

O principal risco mitigado foi o **problema N+1**: todas as associações `@ManyToOne` foram configuradas com `FetchType.LAZY`, evitando que o Hibernate emita uma query adicional por entidade relacionada ao carregar listas.

---

## 3. Estrutura do Projeto

```
code/
├── jvm/                                    # Backend Spring Boot
│   └── src/main/kotlin/workflow/
│       ├── WorkflowMainApplication.kt      # Entry point (@SpringBootApplication)
│       ├── config/
│       │   └── DataInitializer.kt          # Seed inicial de roles, permissões e admin
│       ├── controller/                     # @RestController — endpoints HTTP
│       │   ├── AuthController.kt
│       │   ├── UserController.kt
│       │   ├── WorkflowController.kt
│       │   ├── TaskController.kt
│       │   ├── ExecutionController.kt
│       │   ├── AlertController.kt
│       │   └── ScheduleController.kt
│       ├── dto/                            # Data classes de request/response (imutáveis)
│       │   ├── Auth.kt
│       │   ├── User.kt
│       │   ├── Task.kt
│       │   └── ...
│       ├── entity/                         # @Entity JPA — modelo da base de dados
│       │   ├── User.kt
│       │   ├── Roles.kt
│       │   ├── Permission.kt
│       │   ├── Workflow.kt
│       │   ├── Task.kt
│       │   ├── WorkflowTaskOrder.kt        # Tabela de associação enriquecida
│       │   ├── Execution.kt
│       │   ├── Alert.kt
│       │   ├── UserToken.kt
│       │   └── Timestamp.kt               # Base class com auditoria
│       ├── repository/                     # Interfaces Spring Data JPA
│       ├── security/                       # Spring Security + pipeline de autenticação
│       │   ├── SecurityConfiguration.kt
│       │   ├── CookieAuthenticationFilter.kt
│       │   ├── CustomUserDetailsService.kt
│       │   ├── TokenUtils.kt
│       │   └── pipeline/
│       │       ├── AuthenticationInterceptor.kt
│       │       ├── AuthenticatedUser.kt
│       │       ├── AuthenticatedUserArgumentResolver.kt
│       │       └── RequestTokenProcessor.kt
│       ├── service/                        # @Service — lógica de negócio
│       │   ├── AuthService.kt
│       │   ├── UserService.kt
│       │   ├── WorkflowService.kt
│       │   ├── TaskService.kt
│       │   ├── ExecutionService.kt
│       │   ├── AlertService.kt
│       │   └── ScheduleService.kt
│       └── utils/                          # Utilitários (Either, Problem, Uris)
└── js/                                     # Frontend React + TypeScript + Vite
    └── src/
        ├── api/                            # Clientes HTTP por recurso
        │   ├── client.ts                   # Base fetch com credentials: 'include'
        │   ├── auth.ts
        │   ├── workflows.ts
        │   ├── tasks.ts
        │   ├── executions.ts
        │   ├── schedules.ts
        │   └── users.ts
        ├── components/
        │   ├── Layout.tsx                  # Wrapper com nav sidebar
        │   └── ProtectedRoute.tsx          # Guard de autenticação/role
        ├── contexts/
        │   └── AuthContext.tsx             # Estado global de autenticação + hooks
        ├── pages/                          # Componentes de página por rota
        └── styles/                         # SCSS modular por componente
```

---

## 4. Camada de Persistência — JPA e Hibernate

### 4.1 Como o JPA Converte Entidades em Tabelas

O Jakarta Persistence API (JPA) é uma especificação que define como objetos Java/Kotlin mapeiam para tabelas relacionais. O Hibernate é a implementação utilizada pelo Spring Boot por omissão. Quando o Hibernate processa uma classe anotada com `@Entity`, executa o seguinte processo de mapeamento:

1. **`@Table(name = "...")`** define o nome da tabela. Se omitido, usa o nome da classe por convenção.
2. **`@Id`** marca a chave primária. `@GeneratedValue(strategy = GenerationType.UUID)` instrui o Hibernate a gerar UUIDs antes do INSERT, sem depender de sequences PostgreSQL — conveniente para geração client-side e para evitar colisões em ambientes distribuídos.
3. **`@Column`** mapeia um campo para uma coluna com restrições declarativas (`nullable = false`, `length = 64`, `unique = true`). Estas restrições geram cláusulas `NOT NULL`, `VARCHAR(n)` e `UNIQUE` no DDL gerado.
4. **`@ManyToOne(fetch = FetchType.LAZY)`** + **`@JoinColumn(name = "...")`** cria uma foreign key na tabela corrente. `LAZY` significa que o objeto relacionado não é carregado até ser explicitamente acedido, evitando queries desnecessárias.
5. **`@ManyToMany`** + **`@JoinTable`** cria automaticamente uma tabela de associação com duas foreign keys, sem necessidade de declarar uma entidade para a tabela intermédia (a menos que essa tabela precise de campos adicionais — caso do `WorkflowTaskOrder`).

O Hibernate com `ddl-auto=update` compara o schema existente na base de dados com o modelo de entidades e emite `ALTER TABLE` / `CREATE TABLE` conforme necessário, sem destruir dados existentes. Para produção, recomenda-se `ddl-auto=validate` combinado com ferramentas de migração como Flyway ou Liquibase.

### 4.2 A Classe Base `Timestamp` — Auditoria Automática

Todas as entidades herdam de `Timestamp`, que injeta automaticamente colunas de auditoria sem duplicação de código:

```kotlin
@MappedSuperclass
abstract class Timestamp {
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    var createdAt: LocalDateTime? = null

    @Column(name = "last_updated")
    @UpdateTimestamp
    var lastUpdated: LocalDateTime? = null
}
```

`@MappedSuperclass` instrui o Hibernate a incluir os campos desta superclasse nas tabelas das subclasses sem criar uma tabela própria para ela. `@CreationTimestamp` e `@UpdateTimestamp` são anotações Hibernate que populam automaticamente os campos no momento do INSERT e UPDATE respetivamente, geridas pelo contexto de persistência sem intervenção da camada de serviço.

---

## 5. Modelo de Domínio em Detalhe

### 5.1 Diagrama de Relações entre Entidades

```
users ─────────────────── roles ──────────── role_permission ──── permissions
  │  (role_id FK)            │ (id)               │                    │
  │                          │            (role_id, permission_id)  (resource, action)
  │ (created_by FK)          │
  ▼                          │
workflows ◄────────────────┘ (owner)
  │ (id)
  │
  ▼
workflow_tasks_order  ──────────────► tasks
  │ (workflow_id FK)  (task_id FK)      │
  │ (depends_on_task_id FK) ───────────┘
  │ (task_order: Int)
  │ (retry_policy: Int)
  │ (id: UUID — chave de reordenação)
  │
executions ──► users (triggered_by FK)
  │        ──► tasks (task_id FK, nullable)
  │        ──► workflows (workflow_id FK, nullable)
  │        ──► executions (parent_execution_id FK, nullable)
  ▼
alerts ──► executions (execution_id FK)
```

### 5.2 Entidade `User`

```kotlin
@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false, length = 64)
    var username: String,

    @Column(nullable = false, length = 256)
    var passwordValidation: String,    // BCrypt hash — nunca a password em claro

    @Column(length = 64)
    var displayName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Roles
) : Timestamp()
```

A password é armazenada exclusivamente como hash BCrypt (`BCryptPasswordEncoder` com fator de custo padrão 10). O campo `username` tem restrição `UNIQUE` — o Hibernate emite `ALTER TABLE users ADD CONSTRAINT ... UNIQUE (username)` no DDL. A coluna `role_id` é uma foreign key para a tabela `roles`; a relação `ManyToOne` com `LAZY` fetch garante que o objeto `Roles` não é carregado automaticamente ao consultar listas de utilizadores.

### 5.3 Entidade `Roles` e o Modelo de Permissões Atómicas

```kotlin
@Entity
@Table(name = "roles")
class Roles(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false, length = 64)
    var name: String,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permission",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    val permissions: MutableSet<Permission> = mutableSetOf()
) : Timestamp()
```

O `@JoinTable(name = "role_permission")` instrui o Hibernate a criar a tabela de associação com duas colunas FK: `role_id` e `permission_id`. A relação `@ManyToMany` permite que um role agregue múltiplas permissões atómicas e que uma permissão seja partilhada por vários roles.

As permissões seguem o modelo **Recurso:Ação**, implementado como produto de dois enums:

```kotlin
enum class ResourceType { WORKFLOW, TASK, SCHEDULE, EXECUTION, USER }
enum class ActionType    { READ, WRITE, DELETE, EXECUTE, MANAGE }
```

O mapeamento completo role → permissões, semeado pelo `DataInitializer.kt`, é:

| Role | Permissões |
|---|---|
| `READER` | `WORKFLOW:READ`, `TASK:READ`, `SCHEDULE:READ`, `EXECUTION:READ` |
| `WRITER` | Tudo de READER + `WORKFLOW:WRITE`, `WORKFLOW:DELETE`, `TASK:WRITE`, `TASK:DELETE`, `SCHEDULE:WRITE`, `SCHEDULE:DELETE` |
| `DEV` | `WORKFLOW:READ`, `WORKFLOW:EXECUTE`, `TASK:READ`, `TASK:EXECUTE`, `EXECUTION:READ` |
| `ADMIN` | Todas as permissões anteriores + `USER:MANAGE` |

O `DataInitializer` é anotado com `@Component` e implementa `ApplicationRunner`, executando na inicialização da aplicação. É **idempotente**: verifica se o role `ADMIN` já existe antes de semear (`if (roleRepository.findByName("ADMIN") != null) return`), evitando duplicações em cada restart.

### 5.4 A Tabela `WorkflowTaskOrder` — Análise Aprofundada

Esta é a entidade arquiteturalmente mais relevante do modelo e requer análise detalhada.

```kotlin
@Entity
@Table(name = "workflow_tasks_order")
class WorkflowTaskOrder(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    var workflow: Workflow,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    var task: Task,

    // Nullable: a primeira tarefa não tem predecessora
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_task_id")
    var dependsOnTask: Task? = null,

    @Column(name = "task_order", nullable = false)
    var taskOrder: Int,

    @Column(nullable = false)
    var retryPolicy: Int = 0
) : Timestamp()
```

**Porquê uma entidade dedicada em vez de `@ManyToMany` simples?**

Uma relação `@ManyToMany` padrão entre `Workflow` e `Task` apenas armazenaria os pares `(workflowId, taskId)`. O `WorkflowTaskOrder` é uma **tabela de associação enriquecida** (*rich association table*) que armazena metadados contextuais da relação, impossíveis de representar numa `@ManyToMany` convencional:

| Campo | Tipo | Função |
|---|---|---|
| `id` | UUID | Chave primária própria — permite referenciar esta linha diretamente no endpoint `PATCH /workflows/{id}/reorder` |
| `workflow_id` | FK (UUID) | Liga ao workflow dono desta configuração de execução |
| `task_id` | FK (UUID) | Liga à tarefa a executar |
| `depends_on_task_id` | FK nullable (UUID) | Define o grafo de dependências: esta tarefa só executa após a predecessora terminar |
| `task_order` | Int | Índice de stage. Tarefas com o **mesmo valor** executam **em paralelo**; valores diferentes definem sequência |
| `retry_policy` | Int | Número máximo de tentativas de reexecução em caso de falha, específico para esta tarefa neste workflow |

**Modelo de paralelismo via `task_order`:**

```
task_order = 1  →  [Tarefa A]              (executa primeiro, isolada)
task_order = 2  →  [Tarefa B, Tarefa C]    (executam em paralelo, após A terminar)
task_order = 3  →  [Tarefa D]              (executa após B e C concluírem)
```

Este modelo é simples e eficaz: o motor de execução agrupa tarefas por `task_order`, executa cada grupo em paralelo e avança para o próximo grupo apenas quando todas as tarefas do grupo anterior terminam com sucesso.

**O campo `orderId` no frontend:**

O DTO `WorkflowTaskEntry` expõe `orderId` (UUID do registo `WorkflowTaskOrder`). Este campo é necessário no payload de reordenação enviado ao backend:

```typescript
interface TaskOrderItem {
  orderId: string    // WorkflowTaskOrder.id — identifica a linha a atualizar
  taskOrder: number  // novo valor de stage
}
```

Sem este identificador, o backend não saberia qual linha de `workflow_tasks_order` atualizar — não é possível usar apenas `taskId`, pois a mesma tarefa pode estar associada a múltiplos workflows com ordens distintas.

**Normalização de stages no frontend:**

A função `compactStages()` no `WorkflowDetailPage.tsx` normaliza os valores de `taskOrder` para serem sempre contíguos (1, 2, 3, ...), independentemente dos valores absolutos na base de dados:

```typescript
function compactStages(entries: WorkflowTaskEntry[]): WorkflowTaskEntry[] {
  const uniqueStages = [...new Set(entries.map(e => e.taskOrder))].sort((a, b) => a - b)
  const stageMap = new Map(uniqueStages.map((s, i) => [s, i + 1]))
  return entries.map(e => ({ ...e, taskOrder: stageMap.get(e.taskOrder)! }))
}
```

Esta normalização é puramente visual — os valores persistidos na base de dados mantêm os seus valores originais até uma operação de reordenação explícita.

### 5.5 Entidades de Execução

A tabela `executions` regista cada execução com os seguintes campos chave:

```sql
-- Constraints de domínio na tabela executions
CHECK (triggered_type IN ('MANUAL', 'CRON', 'EVENT'))
CHECK (type           IN ('TASK', 'WORKFLOW'))
CHECK (status         IN ('PENDING', 'RUNNING', 'SUCCESS', 'ERROR'))
```

- **`type = WORKFLOW`**: execução de um pipeline completo; gera execuções filho do tipo `TASK` para cada tarefa executada.
- **`type = TASK`**: execução isolada de uma tarefa individual.
- **`output`**: coluna JSONB que armazena o resultado estruturado da execução (stdout, stderr, código de saída, etc.).
- **`triggered_by`**: FK para o utilizador que despoletou a execução (ou referência ao scheduler para `CRON`).

As execuções filho (task executions dentro de um workflow run) são associadas ao pai via `parent_execution_id`, permitindo ao frontend construir uma vista hierárquica do histórico.

### 5.6 Entidade `Alert`

Alertas são gerados automaticamente pelo `ExecutionService` quando uma execução termina com status `ERROR` ou quando o `retryPolicy` de uma tarefa é esgotado sem sucesso. Cada alerta referencia a execução que o originou e tem um tipo (`WORKFLOW` ou `TASK`) consistente com o tipo da execução pai.

---

## 6. Segurança e RBAC

### 6.1 Visão Geral da Pipeline de Autenticação

O sistema utiliza autenticação **stateless baseada em cookie HttpOnly**, em oposição a JWT ou sessões de servidor. Esta abordagem foi escolhida pelos seguintes motivos:

- **Cookies HttpOnly** não são acessíveis via JavaScript, protegendo contra ataques XSS (uma das vulnerabilidades do OWASP Top 10).
- **Stateless** (sem sessão no servidor) permite escalabilidade horizontal sem sticky sessions ou armazenamento partilhado de sessões.
- O token é um **valor opaco aleatório** — o seu hash SHA-256 é armazenado na tabela `user_tokens`, nunca o valor bruto. Isto mitiga o impacto de uma eventual exposição da base de dados.

A pipeline completa de autenticação por pedido HTTP é:

```
HTTP Request
     │
     ▼
CookieAuthenticationFilter
     │  Lee cookie 'token', calcula SHA-256, valida contra user_tokens
     │  Popula SecurityContextHolder com UserDetails + autoridades
     ▼
AuthenticationInterceptor (HandlerInterceptor)
     │  Injeta AuthenticatedUser nos parâmetros de controller anotados
     ▼
SecurityFilterChain.authorizeHttpRequests
     │  Verifica regras de acesso ao nível da rota (ROLE_x)
     ▼
@PreAuthorize (AOP Proxy)
     │  Verifica regras ao nível do método (SpEL expressions)
     ▼
Controller Method executa
```

### 6.2 `SecurityConfiguration.kt` em Detalhe

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity      // ← ativa @PreAuthorize/@PostAuthorize nos métodos
class SecurityConfig(...) : WebMvcConfigurer {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // API REST stateless — tokens CSRF não se aplicam (sem cookies de sessão geridos
            // pelo servidor; o cookie de token é HttpOnly e não é enviado por formulários)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .addFilterBefore(
                cookieAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .authorizeHttpRequests { auth ->
                // Rotas públicas — sem autenticação necessária
                auth.requestMatchers(
                    "/api/auth/**",
                    "/api/users/register",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()

                // Rotas protegidas por role coarse-grained
                auth.requestMatchers("/api/workflows/**")
                    .hasAnyRole("ADMIN", "WRITER", "READER", "DEV")
                auth.requestMatchers("/api/tasks/**")
                    .hasAnyRole("ADMIN", "WRITER", "READER", "DEV")
                auth.requestMatchers("/api/schedules/**")
                    .hasAnyRole("ADMIN", "WRITER", "READER")
                auth.requestMatchers("/api/logs/**")
                    .hasAnyRole("ADMIN", "WRITER", "READER", "DEV")

                auth.anyRequest().authenticated()
            }
        return http.build()
    }
}
```

`@EnableMethodSecurity` ativa o proxy AOP do Spring que interceta chamadas a métodos anotados com `@PreAuthorize`. Esta anotação aceita SpEL (*Spring Expression Language*):

```kotlin
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'WRITER')")
@PreAuthorize("hasAuthority('WORKFLOW:WRITE')")
```

O prefixo `ROLE_` é uma convenção interna do Spring Security — ao chamar `hasRole("ADMIN")`, o framework procura pela autoridade `ROLE_ADMIN`. Ao usar `hasAuthority(...)`, o prefixo não é aplicado, sendo necessário especificar o nome exato da autoridade.

### 6.3 `CustomUserDetailsService` — Dois Níveis de Autoridades

O Spring Security requer uma implementação de `UserDetailsService` para carregar os dados do utilizador durante a autenticação. O `CustomUserDetailsService` carrega o utilizador da base de dados e constrói o objeto `UserDetails` com **dois níveis de autoridades simultaneamente**:

1. **Autoridade de role coarse-grained**: `ROLE_ADMIN`, `ROLE_WRITER`, `ROLE_READER`, `ROLE_DEV` — usadas nas regras `.hasAnyRole(...)` do `SecurityFilterChain`.
2. **Autoridades de permissão fine-grained**: `WORKFLOW:READ`, `TASK:WRITE`, `USER:MANAGE`, etc., carregadas a partir das permissões associadas ao role — usadas em `@PreAuthorize("hasAuthority('...')")` ao nível do método.

```kotlin
// Exemplo conceptual do CustomUserDetailsService
override fun loadUserByUsername(username: String): UserDetails {
    val user = userRepository.findByUsername(username)
        ?: throw UsernameNotFoundException("User not found: $username")

    val authorities = mutableListOf<GrantedAuthority>()

    // Nível 1: autoridade de role (prefixo ROLE_ obrigatório)
    authorities.add(SimpleGrantedAuthority("ROLE_${user.role.name}"))

    // Nível 2: permissões atómicas do role
    user.role.permissions.forEach { perm ->
        authorities.add(SimpleGrantedAuthority("${perm.resource}:${perm.action}"))
    }

    return org.springframework.security.core.userdetails.User(
        user.username, user.passwordValidation, authorities
    )
}
```

Esta arquitetura permite que as regras de rota sejam simples (role-level) enquanto as regras de método podem ser tão granulares quanto necessário (permission-level), sem duplicação de lógica.

**Referência:** A implementação seguiu o guia [Spring Security — Servlet Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html), nomeadamente as secções sobre `UserDetailsService`, `GrantedAuthority`, e `SecurityFilterChain` com configuração programática Kotlin DSL.

### 6.4 `CookieAuthenticationFilter`

Estende `OncePerRequestFilter` (garantia de execução única por pedido HTTP, mesmo com forward/include). O fluxo de validação é:

1. Extrai o valor do cookie `token` do pedido HTTP.
2. Calcula o SHA-256 do valor recebido.
3. Consulta a tabela `user_tokens` pelo hash calculado.
4. Verifica se o token não expirou (`createdAt + TTL_HOURS > now`).
5. Se válido, chama `CustomUserDetailsService.loadUserByUsername()` para obter autoridades.
6. Cria um `UsernamePasswordAuthenticationToken` com as autoridades e popula o `SecurityContextHolder`.
7. Continua a filter chain com `filterChain.doFilter(request, response)`.

O filtro é registado **antes** do `UsernamePasswordAuthenticationFilter` para que o contexto esteja populado quando o Spring Security avalia as regras de autorização.

### 6.5 `AuthenticationInterceptor` e Injeção de Utilizador

Para além do `SecurityContextHolder`, o sistema mantém um mecanismo paralelo de injeção de utilizador via `HandlerInterceptor`. O `AuthenticationInterceptor` popula o atributo `AuthenticatedUser` no objeto `HttpServletRequest`, que é depois injetado automaticamente em parâmetros de controller através do `AuthenticatedUserArgumentResolver` (implementa `HandlerMethodArgumentResolver`).

Este mecanismo permite que os controllers acedam ao utilizador autenticado de forma type-safe:

```kotlin
// Controller — sem acesso direto ao SecurityContextHolder
@GetMapping("/me")
fun getProfile(@AuthenticatedUser user: AuthenticatedUser): ResponseEntity<MeResponse> =
    ResponseEntity.ok(authService.me(user.username))
```

### 6.6 Fluxo Completo de Registo e Login

**Registo** (`POST /api/auth/register`):
1. Valida unicidade do username na tabela `users`.
2. Resolve o role: usa o enviado no body ou `"READER"` por omissão — garantindo que novos utilizadores têm acesso de leitura imediato, enquanto o admin pode promovê-los posteriormente.
3. Codifica a password com BCrypt.
4. Persiste o utilizador.
5. Retorna `MeResponse { id, username, role }`.

**Login** (`POST /api/auth/login`):
1. Carrega o utilizador por username.
2. Valida a password com `PasswordEncoder.matches()` — comparação BCrypt em tempo constante.
3. Gera um token opaco aleatório (via `SecureRandom`).
4. Armazena o SHA-256 do token na tabela `user_tokens` com TTL de 24 horas (`TOKEN_TTL_HOURS = 24L`).
5. Define cookie HttpOnly `token` com o valor bruto (não o hash).
6. Define cookie de username (não HttpOnly) para uso pelo frontend na UI.

```kotlin
// Configuração do cookie — SecurityConfiguration.kt
private const val SECURE_COOKIES = false
// false = HTTP (desenvolvimento local); true = HTTPS (produção)
```

---

## 7. Lógica de Negócio — Camada de Serviço

### 7.1 Padrão `Either` para Gestão de Erros de Domínio

Os serviços utilizam um tipo `Either<Failure, Success>` para comunicar erros sem recorrer a exceções de fluxo de controlo:

```kotlin
// AuthService.kt
fun register(request: RegisterRequest): Either<AuthError, MeResponse> {
    if (userRepository.findByUsername(request.username) != null)
        return failure(AuthError.UsernameAlreadyTaken)

    val resolvedRoleName = (request.roleName ?: "READER").uppercase()
    val role = roleRepository.findByName(resolvedRoleName)
        ?: return failure(AuthError.RoleNotFound)

    val saved = userRepository.save(User(...))
    return success(MeResponse(id = saved.id, username = saved.username, role = saved.role.name))
}
```

O controller faz exhaustive pattern matching sobre o resultado com `when`:

```kotlin
fun register(...) = when (val result = authService.register(request)) {
    is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
    is Failure -> when (result.value) {
        AuthError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
        AuthError.RoleNotFound         -> Problem.response(400, Problem.roleNotFound)
        AuthError.UserNotFound         -> Problem.response(404, Problem.userNotFound)
        AuthError.InvalidCredentials   -> Problem.response(401, Problem.invalidCredentials)
    }
}
```

O compilador Kotlin verifica exaustividade do `when` sobre `sealed class`, garantindo que nenhum caso de erro é omitido silenciosamente.

### 7.2 Gestão de Utilizadores (`UserService`)

O `UserService` expõe operações CRUD sobre utilizadores, restringidas ao role `ADMIN`. As operações incluem:

- **Listagem de utilizadores**: retorna todos os utilizadores com o seu role atual.
- **Atribuição de role**: `PATCH /api/users/{id}/role` — permite ao administrador promover ou revogar roles após registo. O registo público atribui sempre `READER` por omissão.
- **Criação administrativa**: `POST /api/users` — para o admin criar utilizadores com qualquer role diretamente.

### 7.3 Gestão de Workflows (`WorkflowService`)

O `WorkflowService` gere o ciclo de vida completo dos workflows:

- **Criação**: valida o utilizador proprietário, persiste o workflow e retorna o DTO de resposta.
- **Listagem**: suporta filtragem por utilizador (perfil próprio) e listagem global (para ADMIN).
- **Associação de tarefa** (`linkTask`): cria uma entrada em `workflow_tasks_order` com o próximo `taskOrder` disponível (max atual + 1), associando a tarefa ao workflow.
- **Desassociação** (`unlinkTask`): remove a entrada de `workflow_tasks_order` sem eliminar a tarefa — esta permanece disponível para outros workflows.
- **Reordenação** (`reorderTasks`): recebe uma lista de `TaskOrderItem { orderId, taskOrder }` e atualiza as linhas correspondentes em `workflow_tasks_order` numa única transação `@Transactional`, garantindo atomicidade.
- **Execução** (`runWorkflow`): cria uma entrada de execução com status `PENDING`, delega ao `ExecutionService` e retorna o `executionId` para polling do cliente.

### 7.4 Gestão de Tarefas (`TaskService`)

As tarefas são entidades independentes — existem na tabela `tasks` sem pertencer obrigatoriamente a um workflow. A associação é gerida pelo `WorkflowTaskOrder`.

- **Criação com `workflowId` opcional**: se fornecido, a tarefa é imediatamente associada ao workflow como último stage (criação atómica da tarefa + entrada na tabela de ordem).
- **Listagem por workflow** (`GET /tasks?workflowId=...`): query com JOIN entre `tasks` e `workflow_tasks_order`, retornando `WorkflowTaskEntry` enriquecido com metadados de ordem e dependências.
- **Execução individual** (`POST /tasks/{id}/run`): cria uma execução do tipo `TASK`, executa o handler do tipo da tarefa e retorna `{ executionId, status }` para polling.
- **Atualização**: permite modificar `name`, `type` e `config` (JSONB) sem afetar associações a workflows.

A coluna `config` é do tipo JSONB no PostgreSQL, suportando estruturas heterogéneas por tipo de tarefa:

```json
// HTTP Task
{ "method": "POST", "url": "https://api.example.com/hook", "headers": { "Authorization": "Bearer ..." } }

// Script Task
{ "command": "bash", "fileName": "pipeline.sh", "directory": "/opt/scripts", "args": ["-v", "--dry-run"] }
```

### 7.5 Motor de Execução (`ExecutionService`)

O `ExecutionService` é o núcleo computacional da plataforma. Gere o ciclo de vida completo de execuções com suporte a retry, paralelismo e registo de output.

**Máquina de estados de uma execução:**

```
PENDING
   │
   ▼
RUNNING ──────────────────────────────────► SUCCESS
   │                                           │
   ▼ (erro)                                    │
ERROR ─── retryPolicy > 0? ─── Sim ──► RUNNING (nova tentativa)
             │
             Não
             ▼
          ERROR (final) → gera Alert
```

**Execução de workflow (tipo `WORKFLOW`):**

1. Agrupa as tarefas do `WorkflowTaskOrder` por valor de `task_order`.
2. Processa os grupos sequencialmente por ordem crescente.
3. Dentro de cada grupo (mesmo `task_order`), executa as tarefas **em paralelo** (via coroutines Kotlin ou `CompletableFuture`).
4. Para cada tarefa, respeita o `retryPolicy` específico do `WorkflowTaskOrder` — em caso de erro, tenta novamente até `retryPolicy` vezes.
5. Cria registos filho de execução (tipo `TASK`) associados ao pai para rastreabilidade.
6. Atualiza o status do workflow para `SUCCESS` se todas as tarefas terminarem com sucesso, ou `ERROR` caso contrário.

**Execução de script:** O `ExecutionService` utiliza `ProcessBuilder` para executar scripts do sistema operativo, capturando `stdout` e `stderr` como output JSONB da execução:

```kotlin
val process = ProcessBuilder(command, fileName, *args.toTypedArray())
    .directory(File(directory))
    .start()
val stdout = process.inputStream.bufferedReader().readText()
val exitCode = process.waitFor()
```

**Execução HTTP:** Emite um pedido HTTP conforme a configuração (`method`, `url`, `headers`, `body`) e armazena o status code e body da resposta como output da execução.

### 7.6 Agendamento (`ScheduleService`)

Os schedules associam um workflow a uma expressão CRON. O Spring Scheduling (`@EnableScheduling`) avalia periodicamente os schedules ativos e dispara execuções do tipo `CRON` quando a expressão é satisfeita. Cada schedule armazena o `workflowId`, a expressão CRON, o estado ativo/inativo e o timestamp da última execução.

---

## 8. Camada de Apresentação — Controllers e DTOs

### 8.1 Convenções de API REST

| Método | Padrão de Rota | Semântica |
|---|---|---|
| `GET` | `/api/{recurso}` | Listar todos (com filtros opcionais via query params) |
| `GET` | `/api/{recurso}/{id}` | Obter por ID |
| `POST` | `/api/{recurso}` | Criar novo recurso |
| `PUT` | `/api/{recurso}/{id}` | Atualização total (substitui todos os campos) |
| `PATCH` | `/api/{recurso}/{id}` | Atualização parcial (modifica campos específicos) |
| `DELETE` | `/api/{recurso}/{id}` | Eliminar |

Recursos expostos: `/api/auth`, `/api/users`, `/api/workflows`, `/api/tasks`, `/api/executions`, `/api/alerts`, `/api/schedules`.

### 8.2 DTOs — Separação Rigorosa de Contratos

Nunca são expostas entidades JPA diretamente nas respostas da API. Toda a comunicação é mediada por DTOs (`data class` Kotlin com campos `val` imutáveis):

- **Request DTOs** (`*CreateRequest`, `*UpdateRequest`): validados com `@field:NotBlank`, `@field:NotNull` via `@Valid` nos controllers.
- **Response DTOs** (`*Response`, `*Entry`, `*Summary`): nunca contêm campos internos como `passwordValidation`, IDs de sessão ou referências circulares.

O DTO mais complexo é o `WorkflowTaskEntry`, que agrega dados de duas tabelas:

```kotlin
data class WorkflowTaskEntry(
    // Da tabela tasks
    val taskId: UUID?,
    val name: String,
    val type: String,
    val config: Map<String, Any>,       // JSONB deserializado

    // Da tabela workflow_tasks_order
    val orderId: UUID?,                 // PK da linha de ordem — chave de reordenação
    val taskOrder: Int,                 // stage de execução (paralelismo pelo valor igual)
    val retryPolicy: Int,
    val dependsOnTaskId: UUID?          // referência para grafo de dependências
)
```

O DTO de reordenação em batch:

```kotlin
data class TaskReorderRequest(val items: List<TaskOrderItem>)
data class TaskOrderItem(val orderId: UUID, val taskOrder: Int)
```

### 8.3 Gestão Global de Erros (`@ControllerAdvice`)

Um `GlobalExceptionHandler` centraliza o tratamento de exceções, mapeando tipos para status HTTP:

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) =
        ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "Bad request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Not found"))
}
```

O sistema utiliza também o tipo `Problem` (inspirado no [RFC 7807 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807)) para respostas de erro estruturadas com `Content-Type: application/problem+json`, compatíveis com ferramentas de cliente que esperam respostas de erro padronizadas.

---

## 9. Frontend — React e TypeScript

### 9.1 Arquitetura da SPA

O frontend é uma Single Page Application construída com React 18, TypeScript e Vite (bundler moderno baseado em ES modules nativos, significativamente mais rápido que Webpack em desenvolvimento). A navegação é gerida pelo React Router v6 com rotas declarativas aninhadas.

### 9.2 Gestão de Estado de Autenticação — `AuthContext`

O `AuthContext` é um React Context que disponibiliza o estado de autenticação globalmente a toda a árvore de componentes, evitando *prop drilling*:

```typescript
interface AuthContextValue {
  user: MeResponse | null  // null = não autenticado
  loading: boolean         // true durante verificação inicial de sessão
  login:   (username: string, password: string) => Promise<void>
  logout:  () => Promise<void>
  refresh: () => Promise<void>
}
```

Na inicialização da SPA, é chamado `authApi.me()` para verificar se existe um cookie de sessão válido. Se a resposta for 200, o utilizador é considerado autenticado sem necessidade de nova credencial — o estado de autenticação persiste após refresh da página porque o cookie HttpOnly é enviado automaticamente pelo browser.

### 9.3 RBAC no Frontend — `usePermissions()`

O hook `usePermissions()` deriva autoridades a partir do role do utilizador autenticado:

```typescript
export function usePermissions() {
  const { user } = useAuth()
  const role = user?.role ?? ''

  const isAdmin = role === RoleType.ADMIN
  const isWrite = isAdmin || role === RoleType.WRITER
  const isRead  = isWrite || role === RoleType.READER || role === RoleType.DEV
  const isDev   = isAdmin || role === RoleType.DEV

  return {
    canReadWorkflows:    isRead,
    canWriteWorkflows:   isWrite,
    canDeleteWorkflows:  isWrite,
    canExecuteWorkflows: isWrite,
    canReadTasks:        isRead,
    canWriteTasks:       isWrite,
    canDeleteTasks:      isWrite,
    canReadSchedules:    isRead,
    canWriteSchedules:   isWrite,
    canDeleteSchedules:  isWrite,
    canAccessAdmin:      isAdmin,
    canAccessDev:        isDev,
  }
}
```

**Importante:** Este RBAC no frontend é **exclusivamente para UX** — oculta botões e rotas inacessíveis para melhorar a experiência. A autorização real e definitiva é sempre imposta pelo backend via `@PreAuthorize`. Um utilizador mal-intencionado que manipule o estado do frontend não consegue contornar as regras do servidor.

### 9.4 `ProtectedRoute` — Guard de Rota

```typescript
export function ProtectedRoute({ children, roles }: Props) {
  const { user, loading } = useAuth()
  if (loading) return <div className="loading">Loading…</div>
  if (!user)   return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
```

Garante que utilizadores não autenticados são redirecionados para `/login` e que utilizadores sem o role adequado não acedem a rotas restritas (ex: `/admin` requer `ADMIN`).

### 9.5 `WorkflowDetailPage` — Funcionalidades Principais

Esta é a página mais rica da aplicação, combinando múltiplas funcionalidades interativas:

**Drag-and-drop de tarefas:**
- Implementado com `@hello-pangea/dnd` (fork mantido de `react-beautiful-dnd`).
- Cada drop persiste imediatamente via `PATCH /workflows/{id}/reorder` com a nova ordem calculada.
- O estado visual é atualizado otimisticamente antes da confirmação do servidor.

**Edição inline de stage:**
- Clicar no badge de stage abre um `<input type="number">` inline no lugar do badge.
- `commitStageEdit()` recalcula `compactStages()` após o novo valor e persiste a ordem.

**Execução de workflow com polling:**
- Botão "▶ Run workflow" define todos os tasks como `PENDING` otimisticamente.
- Dispara `POST /workflows/{id}/run`.
- Inicia polling com `setInterval` a 1500ms que chama `executionApi.getById(executionId)`.
- Os badges de status de cada tarefa são atualizados em tempo real a partir de `taskExecutions` na resposta.
- Polling pára quando `status === 'SUCCESS' || status === 'ERROR'`.

**Execução individual de tarefa:**
- Botão "▶" por linha de tarefa, com polling próprio do `executionId` retornado.
- Status RUNNING é definido otimisticamente antes da resposta do servidor.

**Histórico de execuções:**
- Carregado no `useEffect` de mount da página (não apenas ao clicar "Show").
- Atualizado imediatamente ao disparar qualquer execução (entry RUNNING aparece sem aguardar conclusão).
- Atualizado novamente quando o polling termina com estado final.
- Expandível por linha para mostrar `taskExecutions` filhas.

### 9.6 Clientes HTTP e Cross-Origin Credentials

Cada recurso tem um módulo de cliente dedicado (`api/workflows.ts`, `api/tasks.ts`, etc.) que encapsulam os pedidos `fetch`. O cliente base (`api/client.ts`) configura `credentials: 'include'` em todos os pedidos:

```typescript
// client.ts — necessário para envio de cookies cross-origin
const response = await fetch(BASE_URL + path, {
  credentials: 'include',    // envia cookies HttpOnly em pedidos cross-origin
  headers: { 'Content-Type': 'application/json' },
  ...
})
```

Esta configuração é obrigatória para que o browser envie o cookie de autenticação em pedidos para uma origem diferente (ex: frontend em `localhost:5173`, backend em `localhost:8080`). O backend deve ter `Access-Control-Allow-Credentials: true` e uma origem explícita (não `*`) nas headers CORS.

---

## 10. Dificuldades e Aprendizagens

### 10.1 Configuração de Cookies Cross-Origin em Desenvolvimento

Observou-se que a configuração de cookies HttpOnly cross-origin requer alinhamento preciso entre `SameSite`, `Secure` e as headers CORS do backend. Em desenvolvimento local (HTTP), a flag `secure = false` é necessária; em produção (HTTPS) deve ser `true`. A diretiva `SameSite=Lax` foi adotada como compromisso entre segurança e usabilidade, permitindo o envio do cookie em navegações de top-level mas bloqueando-o em contextos de terceiros.

### 10.2 Atomicidade na Reordenação de Tarefas

A operação de reordenação implica atualizar múltiplas linhas de `workflow_tasks_order` num único pedido. Verificou-se que, sem `@Transactional` no método de serviço, uma falha a meio da lista deixaria o workflow num estado de ordem inconsistente. A anotação garante rollback automático em caso de qualquer exceção durante o processamento do batch.

### 10.3 `LazyInitializationException` e Fronteiras de Transação

Observou-se que o acesso a propriedades `FetchType.LAZY` fora de uma transação JPA ativa lança `LazyInitializationException`. Este problema manifestou-se inicialmente em serialização de entidades para DTOs fora do contexto de serviço. A solução adotada foi garantir que todos os métodos de serviço que navegam grafos de objetos estão anotados com `@Transactional`, mantendo a sessão Hibernate ativa durante toda a operação de mapeamento.

### 10.4 Idempotência do `DataInitializer`

Na fase inicial do projeto, reinicializações da aplicação com `ddl-auto=create-drop` eliminavam os dados semeados. A migração para `ddl-auto=update` e a verificação de existência no `DataInitializer` resolveu este problema, tornando o processo de seed idempotente e seguro para múltiplos restarts.

### 10.5 Polling vs WebSockets

Considerou-se a implementação de WebSockets (STOMP over SockJS) para notificações em tempo real de estado de execução. Optou-se por polling a 1500ms por simplicidade de implementação e por ser suficiente para o contexto de uso — execuções de segundos a minutos. A transição para WebSockets permanece identificada como melhoria futura.

### 10.6 Gestão de Contexto de Modelo em Sessões Longas

Observou-se que em sessões de desenvolvimento longas com assistência de IA (Copilot), o contexto acumulado pode levar a sugestões inconsistentes com o estado atual do código. A estratégia adotada foi manter este relatório atualizado e referenciar ficheiros concretos como âncoras de contexto em cada sessão.

---

## 11. Infraestrutura e Implantação

### 11.1 Ambiente Local com Docker

A infraestrutura de desenvolvimento local é gerida com Docker Compose, definida em `src/docker-compose.yml`. A base de dados PostgreSQL 15 é iniciada num container isolado, exposto na porta 5432:

```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: workflowdb
      POSTGRES_USER: workflow
      POSTGRES_PASSWORD: workflow
    ports:
      - "5432:5432"
```

O backend Spring Boot liga-se a este container através das propriedades em `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/workflowdb
spring.datasource.username=workflow
spring.datasource.password=workflow
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
```

### 11.2 Inicialização Automática — `DataInitializer`

O `DataInitializer` (`@Component`, implementa `ApplicationRunner`) executa automaticamente no arranque da aplicação e semeia a base de dados com roles, permissões atómicas e o utilizador administrador padrão (`admin` / `admin123`). A operação é **idempotente** — verifica a existência do role `ADMIN` antes de executar qualquer escrita, sendo seguro em múltiplos restarts.

### 11.3 Processo de Build e Execução

```bash
# 1. Iniciar PostgreSQL
docker-compose -f src/docker-compose.yml up -d

# 2. Arrancar a aplicação Spring Boot
./gradlew bootRun

# 3. Iniciar o frontend (terminal separado)
cd code/js && npm install && npm run dev

# 4. Executar testes
./gradlew test

# 5. Gerar JAR de produção
./gradlew build
```

A aplicação fica disponível em `http://localhost:8080` e o frontend em `http://localhost:5173`. A documentação Swagger da API está acessível em `http://localhost:8080/swagger-ui/index.html`.

### 11.4 Configuração CORS e Cross-Origin

O frontend (`localhost:5173`) comunica com o backend (`localhost:8080`). A configuração CORS no `SecurityConfig` permite a origem do frontend com `allowCredentials = true` — obrigatório para que o browser envie cookies HttpOnly em pedidos cross-origin. Em produção, o frontend pode ser servido como recursos estáticos pelo mesmo servidor, eliminando a necessidade de configuração CORS.

---

## 12. Análise Comparativa com Projetos de Semestres Anteriores

Esta secção analisa os seis projetos de semestres anteriores fornecidos como referência, comparando as escolhas arquiteturais, tecnologias e abordagens documentadas com as adotadas na Workflow Platform. O objetivo é contextualizar as decisões tomadas, identificar boas práticas recorrentes e justificar os desvios.

### 12.1 Visão Geral dos Projetos de Referência

| Projeto | Ano | Domínio | Autenticação | Acesso a Dados | Frontend |
|---|---|---|---|---|---|
| OL39 — DWP | 2022/23 | Aprovação de documentos (ISEL) | OAuth2 + OpenID Connect | JDBC | Web + Mobile |
| OL41 — Teaching Credits | 2023/24 | Registo de horas docentes (ISEL) | Auth server separado | JDBC | React |
| OL42 — AGU Data System | 2023/24 | Gestão de unidades de gás (GALP) | Não detalhado | Spring Data JPA | React + TypeScript + MUI |
| OL46/OL48 — ARBNET | 2024/25 | Gestão de árbitros desportivos | JWT | JDBI + JPA | Não especificado |
| OL50 — Remote Lab | 2024/25 | Laboratório remoto de hardware | OAuth2 (Microsoft Entra) | JDBI | Não especificado |
| **Esta plataforma** | **2025/26** | **Orquestração de workflows** | **Cookie HttpOnly + token opaco** | **Spring Data JPA** | **React + TypeScript + Vite** |

### 12.2 Análise por Projeto

#### OL39 — Document Workflow Platform (DWP)

**Domínio:** Aprovação e circulação de documentos em instituições hierárquicas (ISEL). Digitaliza processos de aprovação realizados por email ou fisicamente, com notificações automáticas por cada transição de estado.

**Pontos fortes:**
- Análise detalhada do estado da arte com comparação de ferramentas de mercado (Nintex, Kissflow, Zapier, Microsoft Power Automate, SharePoint, IBM BPM).
- Caso de estudo concreto com dados reais do ISEL que valida a solução proposta — abordagem *domain-first* exemplar.
- Documentação visual rica: diagramas RBAC0 vs RBAC1, modelo EA completo, capturas de ecrã de todas as interfaces.
- Uso de OAuth2 + OpenID Connect para autenticação federada — tecnicamente robusto para ambientes com identidade institucional centralizada.
- Sistema de notificações por email em cada transição de estado do processo de aprovação.

**Pontos fracos:**
- Dependência de servidor OAuth2 externo aumenta a complexidade de deployment e introduz um ponto de falha adicional.
- Não documenta a justificação da escolha JDBC vs JPA — a decisão de acesso à base de dados não está fundamentada tecnicamente.
- A aplicação móvel mencionada na arquitetura proposta não está documentada em detalhe no relatório.

**Relevância para este projeto:** O modelo RBAC0/RBAC1 documentado no OL39 serviu de referência conceptual para o modelo de permissões atómicas (Recurso:Ação) adotado nesta plataforma. A decisão de *não* usar OAuth2 externo justifica-se pela simplicidade de deployment — um servidor OAuth2 adicional aumenta a infraestrutura necessária sem benefício proporcional para um sistema de automação interna sem requisito de identidade federada.

---

#### OL41 — ISEL Teaching Credits

**Domínio:** Registo, validação descentralizada e consulta de horas lecionadas por docentes do ISEL, substituindo uma folha de cálculo circulada manualmente pela hierarquia institucional.

**Pontos fortes:**
- Separação explícita do servidor de autenticação num componente independente — facilita substituição futura sem impactar o sistema principal, respeitando o princípio de *low coupling*.
- Modelo de estados de validação bem documentado: Waiting Validation → Valid / Invalid / Requested — com semântica clara por estado.
- Hierarquia de utilizadores clara (TSC → Head of Department → Course Coordinator → Faculty) com responsabilidades detalhadas por role numa secção dedicada a requisitos (2.1.1–2.1.4).
- Diagramas de entidade individuais apresentados *antes* do diagrama EA completo — abordagem pedagogicamente mais clara.

**Pontos fracos:**
- O servidor de autenticação separado introduz overhead operacional (dois serviços a gerir em deployment, potencial para divergência de versões).
- Não documenta a justificação tecnológica para JDBC em vez de JPA.
- O estado da arte reconhece não ter encontrado solução equivalente no mercado — poderia ter explorado alternativas como Keycloak para o servidor de autenticação.

**Relevância para este projeto:** A abordagem de servidor de auth separado foi considerada e rejeitada — o `AuthService` interno com tokens opacos oferece controlo total sem infraestrutura adicional. O modelo de estados das execuções desta plataforma (PENDING → RUNNING → SUCCESS → ERROR) é conceptualmente análogo ao modelo de validação (Waiting → Valid/Invalid/Requested) do OL41, aplicado ao domínio de execução de tarefas.

---

#### OL42 — Autonomous Gas Unit Data System

**Domínio:** Gestão, previsão e agendamento automático de consumo de gás em unidades autónomas (GALP), substituindo macros Excel e portais manuais por um sistema integrado com algoritmos preditivos.

**Pontos fortes:**
- **Stack tecnológico idêntico** ao desta plataforma: Kotlin + Spring Boot + PostgreSQL + React + TypeScript — valida as escolhas tecnológicas de forma independente.
- Arquitetura de microserviços (DSF + AGU-PS + sistema principal) bem documentada com justificação clara da separação de responsabilidades e diagramas com tecnologias por componente.
- Integração com modelo de previsão Python via `ProcessBuilder` — padrão semelhante ao executor de tarefas do tipo `SCRIPT` desta plataforma.
- Documentação de formatos de request/response JSON com exemplos concretos (Listings 3.1–3.7) — prática a adotar em versões futuras deste relatório.

**Pontos fracos:**
- A arquitetura de microserviços, embora escalável, introduz overhead operacional e de comunicação inter-serviço considerável para um projeto académico.
- A pipeline de segurança entre microserviços não está documentada — como um microserviço se autentica noutro permanece opaco.
- A integração Python-Kotlin via command-line é frágil em produção (gestão de processos, timeouts, captura de erros de processo).

**Relevância para este projeto:** O OL42 confirma que Kotlin + Spring Boot + PostgreSQL + React + TypeScript é uma stack viável e documentada no contexto desta UC. A decisão de *não* adotar microserviços justifica-se: o domínio de orquestração não requer isolamento de serviços em fases iniciais, e um monólito modular é mais simples de operar, testar e manter. O `ExecutionService` é, contudo, o candidato natural a extração futura para um worker independente. A integração Python via `ProcessBuilder` foi inspiração direta para o executor de tarefas `SCRIPT`.

---

#### OL46/OL48 — ARBNET (Gestão de Equipas de Arbitragem Desportiva)

**Domínio:** Plataforma para gestão de convocatórias, presenças, avaliações e pagamentos de árbitros de natação — substitui múltiplos ficheiros Excel por uma aplicação web centralizada, desenvolvida a partir de necessidades reais de uma organização desportiva.

**Pontos fortes:**
- **JDBI** em vez de JPA — escolha deliberada que valoriza o controlo total sobre SQL gerado, performance previsível e ausência de comportamentos implícitos do ORM.
- Integração de **base de dados não-relacional** (documentos) para relatórios e folhas de pagamento, com justificação técnica clara: a estrutura livre destes documentos não se adapta bem ao modelo relacional rígido.
- Documentação exaustiva do fluxo de negócio (três fases da convocatória) antes de qualquer detalhe técnico — abordagem *domain-first* exemplar.
- **JWT** para autenticação — token stateless com vantagens de portabilidade entre domínios e sem necessidade de consulta à base de dados por pedido.
- Inclusão de **anexos com os ficheiros Excel originais** — documenta o *as-is* para contextualizar o *to-be*, demonstrando rigor de análise de requisitos.

**Pontos fracos:**
- A combinação JDBI + JPA no mesmo projeto sugere inconsistência — não é claro quando cada abordagem é usada e porquê.
- JWT stateless não pode ser invalidado pelo servidor sem armazenamento de blacklist — risco de segurança (tokens roubados são válidos até expirar) não abordado no relatório.
- A base de dados não-relacional adiciona complexidade operacional (dois sistemas de persistência distintos a gerir e monitorizar).

**Relevância para este projeto:** A escolha de JDBI pelo OL46 reforça a validade da comparação JPA vs SQL Mapper documentada na Secção 2.4 deste relatório. JWT foi considerado e rejeitado em favor de tokens opacos — o cookie HttpOnly protege contra XSS, e o armazenamento do hash SHA-256 na base de dados permite invalidação imediata no logout. A ausência de base de dados não-relacional justifica-se pelo uso de JSONB do PostgreSQL para campos de estrutura flexível (`config` das tasks, `output` das execuções) — eliminando a necessidade de um segundo sistema de persistência.

---

#### OL50 — Remote Lab

**Domínio:** Plataforma de acesso remoto a laboratórios de hardware (FPGAs, microcontroladores) para ensino à distância no ISEL, com sistema de filas de espera e controlo via terminal.

**Pontos fortes:**
- **Capítulo dedicado a deployment** (Capítulo 6) com CI/CD, containerização e configuração de domínio — secção ausente na maioria dos relatórios anteriores e que inspirou diretamente a Secção 11 deste documento.
- **Server-Sent Events (SSE)** para comunicação em tempo real do servidor para o cliente — alternativa mais eficiente ao polling: mantém uma ligação HTTP persistente, eliminando o overhead de pedidos repetidos a cada intervalo.
- **Apêndices detalhados**: documentação da base de dados (Apêndice A), exemplos Postman (Apêndice C), configuração de autenticação OAuth2 (Apêndice D) — referência de completude documental para projetos futuros.
- Fila de espera (*waiting queue*) para laboratórios documentada com diagrama de estados — exemplo de lógica de negócio complexa bem especificada.
- **OAuth2 com Microsoft Entra** — adequado para contexto institucional com contas Azure AD já existentes nos alunos.

**Pontos fracos:**
- Dependência de Microsoft Entra introduz acoplamento a serviço externo Microsoft — falha no serviço externo implica falha na autenticação.
- JDBI com SQL manual aumenta o esforço de manutenção em schemas evolutivos comparativamente a JPA com `ddl-auto=update`.
- A comunicação com hardware via terminal não está completamente especificada nos protocolos de baixo nível.

**Relevância para este projeto:** O SSE do OL50 é a alternativa técnica mais relevante ao polling de 1500ms implementado nesta plataforma para atualizações de estado de execuções. A diferença é significativa: SSE mantém uma ligação persistente, eliminando o overhead de intervalo de polling — mas requer gestão de conexões, reconexão em falha e compatibilidade com proxies HTTP. A decisão de polling foi tomada por simplicidade, com migração para SSE ou WebSockets identificada como melhoria futura (Secção 13). O capítulo de deployment do OL50 é o modelo de referência direto para a Secção 11 deste documento.

---

### 12.3 Tabela Comparativa de Decisões Técnicas

| Decisão | OL39 | OL41 | OL42 | OL46/48 | OL50 | **Esta Plataforma** | Justificação da escolha |
|---|---|---|---|---|---|---|---|
| **Autenticação** | OAuth2 + OIDC | Auth server separado | Não detalhado | JWT | OAuth2 (Entra) | Cookie HttpOnly + token opaco + SHA-256 | Controlo total, sem dependência externa, invalidação imediata no logout, proteção XSS nativa |
| **Acesso a dados** | JDBC | JDBC | Spring Data JPA | JDBI + JPA | JDBI | Spring Data JPA | Produtividade com schema estável; JPA gere relações N:M enriquecidas (`WorkflowTaskOrder`) automaticamente |
| **Base de dados** | PostgreSQL | PostgreSQL | PostgreSQL | Relacional + NoSQL | PostgreSQL | PostgreSQL 15 + JSONB | JSONB elimina necessidade de NoSQL para campos de estrutura flexível (`config`, `output`) |
| **Tempo real** | Email (async) | — | — | Notificações | SSE | Polling 1500ms | SSE identificado como evolução futura; polling suficiente para duração típica das execuções |
| **Frontend** | Web + Mobile | React | React + TS + MUI | Não especificado | Não especificado | React + TypeScript + Vite + SCSS | SCSS modular dá controlo total de estilos sem dependência de biblioteca de componentes |
| **Microserviços** | Não | Não | Sim (3 serviços) | Não | Não | Não (monólito modular) | Overhead não justificado em fase inicial; `ExecutionService` é candidato natural a extração futura |
| **RBAC** | RBAC0/RBAC1 | Hierárquico | Não detalhado | JWT claims | Hierárquico | 2 níveis: role coarse-grained + permissões atómicas | Granularidade máxima sem complexidade de ABAC; autoridades carregadas pelo `CustomUserDetailsService` |

### 12.4 Secções Presentes nos Relatórios Anteriores e o seu Estado neste Documento

| Secção | Presença nos anteriores | Estado neste documento |
|---|---|---|
| Resumo (PT) + Abstract (EN) | Todos (100%) | ✓ Adicionado |
| Lista de Acrónimos | Todos (100%) | ✓ Adicionado |
| Estado da Arte | OL39, OL41, OL46, OL50 (67%) | ✓ Adicionado (Secção 1.2) |
| Organização do documento | Todos (100%) | ✓ Adicionado (Secção 1.3) |
| Diagrama de arquitetura com tecnologias por componente | Todos (100%) | Descrito textualmente (Secção 3) |
| Modelo EA com diagramas de entidade individuais | Todos (100%) | Descrito textualmente (Secção 5) |
| Fluxo de utilização / User journey | OL39, OL41, OL46 (50%) | Coberto por serviço (Secção 7) |
| Capítulo de deployment / infraestrutura | OL50 (17%) | ✓ Adicionado (Secção 11) |
| Exemplos de request/response JSON (Listings) | OL42 (17%) | Parcialmente (Secção 8) |
| Apêndices (Postman, SQL schema, configuração) | OL50, OL46 (33%) | Não incluído — trabalho futuro |
| Capturas de ecrã do frontend | Todos (100%) | Não incluído — fora do âmbito do formato Markdown |
| Análise comparativa com projetos anteriores | Nenhum (0%) | ✓ Adicionado (Secção 12 — exclusivo deste relatório) |

---

## 13. Conclusão

A Workflow Platform constitui uma implementação completa de uma REST API de orquestração de processos, demonstrando a aplicação prática de conceitos fundamentais de Engenharia de Software:

- **Separação de responsabilidades** através da arquitetura Controller → Service → Repository, com DTOs isolando os contratos da API das entidades internas.
- **Segurança por defeito** com RBAC de dois níveis: coarse-grained por rota (SecurityFilterChain) e fine-grained por método (@PreAuthorize), suportado por autoridades atómicas carregadas pelo `CustomUserDetailsService`.
- **Modelação relacional rica** através da entidade `WorkflowTaskOrder`, que vai além de uma simples tabela de associação ao armazenar `taskOrder`, `retryPolicy` e `dependsOnTaskId` — suportando paralelismo, sequenciamento e resiliência numa única estrutura.
- **Type Safety end-to-end** com Kotlin no backend (null-safety, sealed classes, data classes) e TypeScript no frontend (interfaces tipadas, enums, hooks).
- **Arquitetura Stateless** com autenticação por cookie HttpOnly e tokens opacos com hash SHA-256, conforme as boas práticas do Spring Security Reference.

**Próximos passos identificados:**

1. Migração do polling para WebSockets com Spring WebFlux para notificações em tempo real sem overhead de pedidos repetidos.
2. Implementação de notificações de alerta via email/webhook quando execuções terminam em `ERROR`.
3. Suporte a expressões CRON avançadas com fuso horário configurável por schedule.
4. Testes de integração com Testcontainers para PostgreSQL, validando o comportamento real de queries JPA.
5. Migração de `ddl-auto=update` para Flyway em produção, garantindo migrações de schema versionadas e reversíveis.
6. Separação do motor de execução para um serviço dedicado com mensageria assíncrona (RabbitMQ/Kafka), tornando a API um orchestrator stateless puro.

A escalabilidade do sistema é atualmente limitada pela execução síncrona de tarefas no mesmo processo da API. A arquitetura em camadas adotada facilita esta separação futura: o `ExecutionService` pode ser extraído para um worker independente sem alterações nas camadas de Controller, DTO ou Repository. - Plataforma de Orquestração de Workflows
