# 4. SOLUÇÃO PROPOSTA

## 4.1 Decisões Arquiteturais

### 4.1.1 Kotlin sobre Java

Optou-se por Kotlin 2.3 em detrimento de Java para o desenvolvimento do *backend*. A principal motivação foi a **segurança em relação a valores nulos** (*null safety*) integrada no sistema de tipos da linguagem, que elimina uma classe inteira de erros em tempo de execução. A expressividade de Kotlin, nomeadamente as *extension functions*, as *data classes* e os *sealed classes*, reduziu significativamente o código *boilerplate* face a uma implementação equivalente em Java. A total interoperabilidade com o ecossistema JVM e com o Spring Framework assegurou que nenhuma funcionalidade ficasse comprometida.

O ficheiro `code/jvm/build.gradle.kts` configura o compilador Kotlin com o *plugin* Spring (`kotlin("plugin.spring")`) e o *plugin* JPA (`kotlin("plugin.jpa")`), necessários para a geração automática de proxies e construtores sem argumentos requeridos pelo Hibernate.

### 4.1.2 Spring Boot 3.5

O Spring Boot 3.5 foi selecionado pela sua maturidade, pela integração nativa com Spring Data JPA, Spring Security e Spring Scheduler, e pela produtividade que oferece no desenvolvimento de APIs REST. A versão 3.5 assegura compatibilidade com Java 25 (configurado como `toolchain { languageVersion = JavaLanguageVersion.of(25) }` em `build.gradle.kts`) e com as mais recentes especificações Jakarta EE.

### 4.1.3 Autenticação por Cookie sobre JWT

Optou-se por um modelo de autenticação **sem estado (*stateless*) baseado em *cookies* HttpOnly**, em alternativa à abordagem JWT. A motivação principal foi a **segurança contra ataques XSS**: ao contrário de tokens JWT armazenados em `localStorage`, os *cookies* com atributo `HttpOnly` não são acessíveis via JavaScript no navegador. O token opaco é armazenado na tabela `user_tokens` com *hash* SHA-256, garantindo que mesmo em caso de comprometimento da base de dados os tokens em texto claro não sejam expostos.

A implementação encontra-se em `code/jvm/src/main/kotlin/workflow/security/CookieAuthenticationFilter.kt` e `TokenUtils.kt`.

### 4.1.4 Padrão Either para Gestão de Erros

Os serviços de domínio nunca lançam exceções para falhas esperadas. Em alternativa, utilizam o padrão funcional `Either<E, T>`, implementado em `code/jvm/src/main/kotlin/workflow/utils/Either.kt`:

```kotlin
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()
}

typealias Failure<L> = Either.Left<L>
typealias Success<R> = Either.Right<R>

fun <L> failure(value: L): Either<L, Nothing> = Either.Left(value)
fun <R> success(value: R): Either<Nothing, R> = Either.Right(value)
```

`Left` representa falha (`Failure`); `Right` representa sucesso (`Success`). Os controladores utilizam expressões `when` exaustivas, garantidas pelo compilador, para tratar todos os casos de falha.

### 4.1.5 RBAC com Autoridades Granulares

O controlo de acesso é implementado através de autoridades granulares do formato `recurso:ação` (e.g., `workflow:read`, `workflow:write`), em vez de verificações de papel (*role*) diretas. Esta abordagem desacopla a lógica de autorização dos papéis concretos, tornando a adição de novos papéis transparente para os controladores. O mapeamento de papéis para autoridades é definido em `code/jvm/src/main/kotlin/workflow/config/DataInitializer.kt`.

---

## 4.2 Estrutura do Projeto

O projeto é um monorepo com dois módulos principais:

```
PS/
├── code/
│   ├── jvm/                  # Backend — Kotlin 2.3, Spring Boot 3.5, Java 25
│   │   └── src/main/kotlin/workflow/
│   │       ├── controller/   # AuthController, WorkflowController, TaskController,
│   │       │                 # UserController, ScheduleController
│   │       ├── dto/          # Auth.kt, Workflow.kt, Task.kt, User.kt, Schedule.kt
│   │       ├── entity/       # Todas as entidades JPA + enums/
│   │       ├── repository/   # Interfaces JpaRepository
│   │       ├── service/      # Serviços de domínio + utils/ServiceErrors.kt
│   │       ├── security/     # SecurityConfiguration, CookieAuthenticationFilter,
│   │       │                 # CustomUserDetailsService, TokenUtils
│   │       ├── config/       # DataInitializer, GlobalExceptionHandler, OpenApiConfig
│   │       ├── scheduler/    # ScheduleDispatchJob
│   │       └── utils/        # Either.kt, Problem.kt, Uris.kt
│   └── js/                   # Frontend — React 18, TypeScript ~6.0.2, Vite 6
│       └── src/
│           ├── api/          # client.ts + módulos por recurso
│           ├── contexts/     # AuthContext.tsx
│           ├── components/   # Layout, ProtectedRoute
│           └── pages/        # 10 páginas
└── docs/
    └── DOC.md
```

---

## 4.3 Modelo de Domínio em Detalhe

O modelo de domínio é composto pelas seguintes entidades JPA, todas definidas em `code/jvm/src/main/kotlin/workflow/entity/`:

| Entidade | Tabela | Descrição |
|----------|--------|-----------|
| `User` | `users` | Utilizador autenticado; possui um `Roles` e é dono de `Workflow` |
| `Roles` | `roles` | Papel do utilizador: `ADMIN`, `WRITER`, `READER`, `DEV` |
| `Permission` | `permissions` | Par `ResourceType` × `ActionType`; atribuído a `Roles` |
| `Workflow` | `workflows` | Fluxo de trabalho; pertence a `User`; ADMIN vê todos |
| `Task` | `tasks` | Tarefa configurável; campo `config` é JSONB |
| `WorkflowTaskOrder` | `workflow_tasks_order` | Liga `Workflow` a `Task`; `taskOrder` define paralelismo |
| `Execution` | `executions` | Execução de `Workflow`; `status`: PENDING→RUNNING→SUCCESS/ERROR; `output` é JSONB |
| `Alert` | `alerts` | Alerta associado a uma `Execution` |
| `Schedule` | `schedules` | Agendamento com expressão *cron*; ativado por `ScheduleDispatchJob` |
| `UserToken` | `user_tokens` | Token de sessão opaco, armazenado com *hash* SHA-256 |
| `Timestamp` | *(base)* | Classe base com `createdAt` e `lastUpdated` (`LocalDateTime`) |

Todas as entidades estendem `Timestamp`. As relações utilizam `FetchType.LAZY` para evitar *N+1 queries* involuntárias. Os campos JSONB (`Task.config`, `Execution.output`) utilizam a anotação `@JdbcTypeCode(SqlTypes.JSON)`.

A entidade `WorkflowTaskOrder` suporta execução paralela: tarefas com o mesmo valor de `taskOrder` são executadas em paralelo; valores distintos definem ordem sequencial.

Os enumerados `ResourceType` e `ActionType`, definidos em `entity/enums/`, representam os recursos e ações possíveis para o sistema de permissões.

---

## 4.4 Segurança e RBAC

### Modelo de Autenticação

A autenticação é **sem estado e baseada em *cookie*** — não é utilizado JWT. O fluxo completo é:

1. `POST /api/auth/login` → `AuthService` valida as credenciais → gera token opaco → persiste `UserToken` com *hash* SHA-256 → define *cookie* `token` com atributos `HttpOnly` e `SameSite=Strict`.
2. Em cada pedido subsequente: `CookieAuthenticationFilter` (`security/CookieAuthenticationFilter.kt`) lê o *cookie* `token` → `RequestTokenProcessor` (`security/pipeline/RequestTokenProcessor.kt`) valida contra a base de dados → `CustomUserDetailsService` carrega o utilizador → popula o `SecurityContextHolder`.
3. As anotações `@PreAuthorize` funcionam porque o `SecurityContextHolder` está populado antes de cada invocação do controlador.
4. `POST /api/auth/logout` elimina a linha `UserToken`, invalidando a sessão.

Os *endpoints* públicos (sem autenticação): `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, conforme configurado em `security/SecurityConfiguration.kt`.

### Modelo RBAC

Os papéis disponíveis são `ADMIN`, `WRITER`, `READER` e `DEV`, cada um com um conjunto de autoridades configurado em `config/DataInitializer.kt`:

| Papel | Autoridades |
|-------|-------------|
| `ADMIN` | Todas as autoridades; vê todos os *workflows* |
| `WRITER` | `workflow:read`, `workflow:write`, `workflow:delete`, `workflow:execute` |
| `READER` | `workflow:read` |
| `DEV` | `workflow:read`, acesso a ferramentas de desenvolvimento |

As autoridades disponíveis são:

| Autoridade | Utilização |
|------------|------------|
| `workflow:read` | GET *workflows*, execuções |
| `workflow:write` | POST/PUT/PATCH *workflows*, tarefas, agendamentos |
| `workflow:delete` | DELETE *workflows*, tarefas |
| `workflow:execute` | POST `/run`, POST `/cancel` |
| `isAuthenticated()` | Perfil, leituras com âmbito de utilizador |

### 4.4.1 `@PreAuthorize` — Mecanismo Técnico Detalhado

A anotação `@PreAuthorize` é processada pelo Spring Security através de Spring AOP, antes da invocação do método anotado. A expressão `hasAuthority('workflow:read')` é avaliada contra a coleção de `GrantedAuthority` do `Authentication` presente no `SecurityContextHolder`. O processamento ocorre na cadeia de filtros antes de qualquer lógica de controlador.

Exemplo de utilização nos controladores (`controller/WorkflowController.kt`):

```kotlin
@GetMapping(Uris.Workflows.BASE)
@PreAuthorize("hasAuthority('workflow:read')")
fun list(authentication: Authentication): ResponseEntity<Any> =
    when (val result = workflowService.list(authentication.name)) {
        is Success -> ResponseEntity.ok(result.value)
        is Failure -> when (result.value) {
            WorkflowError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
            WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
        }
    }
```

**Nunca se utiliza `hasRole()`** nos controladores — apenas `hasAuthority()` com o formato `recurso:ação`.

---

## 4.5 Lógica de Negócio — Camada de Serviço

### Padrão Either

Cada serviço retorna `Either<XxxError, T>`. As classes de erro são *sealed classes* definidas em `service/utils/ServiceErrors.kt`:

```kotlin
sealed class AuthError {
    object UsernameAlreadyTaken : AuthError()
    object RoleNotFound         : AuthError()
    object InvalidCredentials   : AuthError()
    object UserNotFound         : AuthError()
    object InsecurePassword     : AuthError()
}

sealed class WorkflowError {
    object WorkflowNotFound : WorkflowError()
    object UserNotFound     : WorkflowError()
}

sealed class TaskError {
    object TaskNotFound     : TaskError()
    object WorkflowNotFound : TaskError()
    object UserNotFound     : TaskError()
    object AlreadyLinked    : TaskError()
    object NotLinked        : TaskError()
}

sealed class UserError {
    object UsernameAlreadyTaken : UserError()
    object RoleNotFound         : UserError()
    object UserNotFound         : UserError()
}

sealed class ScheduleError {
    object ScheduleNotFound      : ScheduleError()
    object WorkflowNotFound      : ScheduleError()
    object UserNotFound          : ScheduleError()
    object InvalidCronExpression : ScheduleError()
}

sealed class ExecutionError {
    object TaskNotFound     : ExecutionError()
    object WorkflowNotFound : ExecutionError()
    object UserNotFound     : ExecutionError()
}
```

O compilador Kotlin garante exaustividade nas expressões `when` nos controladores: caso seja adicionado um novo caso de erro sem correspondente no controlador, a compilação falha. Este mecanismo elimina silenciosamente os erros não tratados.

### Serviços Disponíveis

| Serviço | Ficheiro | Responsabilidade |
|---------|----------|-----------------|
| `AuthService` | `service/AuthService.kt` | Registo, *login*, *logout*, perfil |
| `WorkflowService` | `service/WorkflowService.kt` | CRUD de *workflows*, ligação de tarefas, execução |
| `TaskService` | `service/TaskService.kt` | CRUD de tarefas, execução isolada |
| `UserService` | `service/UserService.kt` | Gestão de utilizadores, atribuição de papéis |
| `ExecutionService` | `service/ExecutionService.kt` | Consulta e cancelamento de execuções |
| `ScheduleService` | `service/ScheduleService.kt` | CRUD de agendamentos, validação de *cron* |
| `ServiceHelpers` | `service/ServiceHelpers.kt` | Utilitários partilhados (e.g., `findUser`) |

Todas as operações de leitura são anotadas com `@Transactional(readOnly = true)`; as de escrita com `@Transactional`.

---

## 4.6 Camada de Apresentação — Controllers e DTOs

### Controllers

Os cinco controladores REST (`controller/`) seguem um padrão uniforme:

- Anotados com `@RestController`, `@Tag` (OpenAPI) e `@RequestMapping`.
- Cada *endpoint* tem `@Operation` e `@ApiResponses`, com respostas de erro a utilizar `mediaType = Problem.MEDIA_TYPE`.
- Delegam toda a lógica ao serviço correspondente e mapeiam o resultado `Either` para `ResponseEntity`.

### Problem RFC 7807

Todos os erros seguem o formato RFC 7807 (`application/problem+json`), através de `Problem.response(status, Problem.xxx)` definido em `utils/Problem.kt`. Nunca se constroem *strings* de erro ad-hoc nos controladores.

### DTOs

Os DTOs de pedido são `data class` com anotações de validação (`@field:NotBlank`, `@field:Size`). Os DTOs de resposta são `data class` com campos `val`. O mapeamento entidade→DTO é realizado através de *extension functions* privadas dentro do serviço correspondente.

### Rotas

Todas as constantes de rota estão definidas em `utils/Uris.kt`. Nunca se utilizam *strings* de caminho codificadas diretamente nas anotações de mapeamento.
