# 5. IMPLEMENTAÇÃO DE INFRAESTRUTURA

## 5.1 Camada de Persistência — JPA e Hibernate

A persistência de dados é gerida através de **Spring Data JPA** com **Hibernate** como implementação ORM, sobre uma base de dados **PostgreSQL 15**. Esta combinação foi preferida sobre alternativas como JDBI ou JOOQ pela integração nativa com o Spring Boot, pelo suporte automático a migrações de esquema e pela produtividade oferecida pelos repositórios derivados.

Cada entidade JPA é uma classe regular (não `data class`) que estende a classe base `Timestamp`, definida em `entity/Timestamp.kt`:

```kotlin
@MappedSuperclass
abstract class Timestamp {
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
}
```

Todos os identificadores são do tipo `UUID`, gerados automaticamente (`@GeneratedValue(strategy = GenerationType.UUID)`). Todas as associações utilizam `FetchType.LAZY` para controlo explícito de *queries*.

Os repositórios (`repository/`) são interfaces que estendem `JpaRepository<T, UUID>`, utilizando apenas métodos derivados de nomes (e.g., `findAllByOwnerId`) sem JPQL manual, salvo quando estritamente necessário.

A configuração da base de dados é gerida através de variáveis de ambiente e do ficheiro `src/docker-compose.yml`, que define o contentor PostgreSQL 15 utilizado em desenvolvimento.

---

## 5.2 Modelo de Dados e Entidades

### Entidades Principais

Todas as entidades estão definidas em `code/jvm/src/main/kotlin/workflow/entity/`.

#### `User` e `Roles`

```kotlin
@Entity @Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 64)
    var username: String,

    @Column(nullable = false)
    var passwordHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    var role: Roles? = null
) : Timestamp()
```

#### `Workflow`

```kotlin
@Entity @Table(name = "workflows")
class Workflow(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 64)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var createdBy: User
) : Timestamp()
```

#### `Task` (com campo JSONB)

```kotlin
@Entity @Table(name = "tasks")
class Task(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 64)
    var name: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var config: Map<String, Any> = mutableMapOf()
) : Timestamp()
```

#### `Execution` (com estado e saída JSONB)

```kotlin
@Entity @Table(name = "executions")
class Execution(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    var workflow: Workflow,

    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus = ExecutionStatus.PENDING,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var output: Map<String, Any>? = null
) : Timestamp()
```

O ciclo de vida do `status` de uma execução é: `PENDING` → `RUNNING` → `SUCCESS` ou `ERROR`.

#### `WorkflowTaskOrder`

```kotlin
@Entity @Table(name = "workflow_tasks_order")
class WorkflowTaskOrder(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY) var workflow: Workflow,
    @ManyToOne(fetch = FetchType.LAZY) var task: Task,

    @Column(nullable = false)
    var taskOrder: Int
) : Timestamp()
```

Tarefas com o mesmo `taskOrder` são executadas em paralelo; valores crescentes definem a ordem sequencial.

#### `Schedule`

```kotlin
@Entity @Table(name = "schedules")
class Schedule(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY) var workflow: Workflow,

    @Column(nullable = false)
    var cronExpression: String,

    @Column(nullable = false)
    var active: Boolean = true
) : Timestamp()
```

O componente `scheduler/ScheduleDispatchJob.kt` avalia periodicamente as expressões *cron* e dispara execuções dos *workflows* correspondentes.

### Tabela Resumo de Entidades

| Entidade | Tabela | Campos Relevantes |
|----------|--------|-------------------|
| `User` | `users` | `username`, `passwordHash`, `role` (FK) |
| `Roles` | `roles` | `name` (ADMIN/WRITER/READER/DEV), `permissions` |
| `Permission` | `permissions` | `resourceType` (enum), `actionType` (enum) |
| `Workflow` | `workflows` | `name`, `createdBy` (FK User) |
| `Task` | `tasks` | `name`, `config` (JSONB) |
| `WorkflowTaskOrder` | `workflow_tasks_order` | `workflow` (FK), `task` (FK), `taskOrder` |
| `Execution` | `executions` | `workflow` (FK), `status`, `output` (JSONB) |
| `Alert` | `alerts` | `execution` (FK), mensagem de alerta |
| `Schedule` | `schedules` | `workflow` (FK), `cronExpression`, `active` |
| `UserToken` | `user_tokens` | `user` (FK), `tokenHash` (SHA-256), `expiresAt` |
| `Timestamp` | *(base)* | `createdAt`, `lastUpdated` |

---

## 5.3 Backend — Spring Boot 3.5 e Kotlin

### Stack Tecnológica

| Componente | Versão |
|------------|--------|
| Kotlin | 2.3.0 |
| Spring Boot | 3.5.0 |
| Java toolchain | 25 |
| Spring Data JPA | gerido pelo Boot |
| Driver PostgreSQL | gerido pelo Boot |
| MockK | 1.14.0 |
| springmockk | 4.0.2 |
| JUnit 5 | 5.11.4 |
| JaCoCo | 0.8.13 (limiar 80%) |

Configuração em `code/jvm/build.gradle.kts`.

### Padrão de Controlador

Todos os controladores seguem o mesmo padrão, exemplificado com `WorkflowController`:

```kotlin
@RestController
@Tag(name = "Workflows", description = "Workflow CRUD e execução manual")
class WorkflowController(
    private val workflowService: WorkflowService,
    private val executionService: ExecutionService,
    private val taskService: TaskService
) {
    @PostMapping(Uris.Workflows.BASE)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Criar workflow")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Criado"),
        ApiResponse(responseCode = "404", description = "Utilizador não encontrado",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun create(
        @Valid @RequestBody request: WorkflowCreateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.create(request, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
            }
        }
}
```

### Estratégia de Testes

Os testes são unitários puros com MockK, sem contexto Spring (`@SpringBootTest` não é utilizado):

```kotlin
class WorkflowControllerTest {
    private lateinit var service: WorkflowService
    private lateinit var controller: WorkflowController
    private lateinit var auth: Authentication

    @BeforeEach fun setup() {
        service = mockk()
        controller = WorkflowController(service, mockk(), mockk())
        auth = mockk(); every { auth.name } returns "alice"
    }

    @Test fun `list returns 200 with workflows`() {
        every { service.list("alice") } returns success(listOf())
        assertEquals(200, controller.list(auth).statusCode.value())
    }
}
```

A cobertura é medida com JaCoCo (limiar mínimo de 80% de linhas). Relatório disponível em `build/reports/jacoco/`.

### Documentação OpenAPI

Todos os *endpoints* estão documentados com SpringDoc OpenAPI (`config/OpenApiConfig.kt`). A interface Swagger UI está disponível em `/swagger-ui/index.html`. Os erros utilizam sempre `Content-Type: application/problem+json` (RFC 7807).

---

## 5.4 Frontend — React e TypeScript

### Stack Tecnológica

| Componente | Versão |
|------------|--------|
| React | 18.3.1 |
| TypeScript | ~6.0.2 |
| Vite | 6.3.0 |
| React Router | 6.28.0 |
| Vitest | 3.1.3 |
| @testing-library/react | 16.3.0 |
| @testing-library/user-event | 14.6.1 |
| @vitest/coverage-v8 | 3.1.3 |
| Sass | 1.99.0 |
| @hello-pangea/dnd | 18.0.1 |

Configuração em `code/js/package.json`. Servidor de desenvolvimento: `http://localhost:5173` com *proxy* `/api` → `http://localhost:8080`.

### Camada de API

Todas as chamadas ao *backend* passam pelo cliente centralizado `src/api/client.ts`, que:
- Envia sempre `credentials: 'include'` (autenticação por *cookie*)
- Define `Content-Type: application/json` quando existe corpo
- Retorna `undefined` para respostas `204 No Content`
- Lança `Error` com a mensagem extraída do corpo Problem (campos `title`/`detail`/`message`)

Os módulos de API (`auth.ts`, `workflows.ts`, `tasks.ts`, `executions.ts`, `schedules.ts`, `users.ts`) expõem funções tipadas que delegam no cliente.

### Contexto de Autenticação

O `AuthContext.tsx` é a única fonte de estado de autenticação:

```typescript
// Hooks disponíveis dentro de <AuthProvider>:
const { user, loading, login, logout, refresh } = useAuth()

const {
  canReadWorkflows, canWriteWorkflows, canDeleteWorkflows, canExecuteWorkflows,
  canReadTasks, canWriteTasks, canDeleteTasks,
  canReadSchedules, canWriteSchedules, canDeleteSchedules,
  canAccessAdmin, canAccessDev
} = usePermissions()

const isAdmin = useHasRole(RoleType.ADMIN)
```

Nunca se verifica `user.role === 'ADMIN'` diretamente nos componentes — utiliza-se sempre `usePermissions()` ou `useHasRole()`.

### Mapa de Rotas

Definido em `src/App.tsx` com React Router 6. As rotas protegidas são envolvidas em `<ProtectedRoute>`:

| Caminho | Componente | Acesso |
|---------|-----------|--------|
| `/login` | `LoginPage` | Público |
| `/register` | `RegisterPage` | Público |
| `/dashboard` | `DashboardPage` | Todos autenticados |
| `/tasks` | `TasksPage` | Todos autenticados |
| `/tasks/new` | `TaskFormPage` | ADMIN, WRITER |
| `/tasks/:id/edit` | `TaskFormPage` | ADMIN, WRITER |
| `/workflows/new` | `WorkflowFormPage` | ADMIN, WRITER |
| `/workflows/:id` | `WorkflowDetailPage` | Todos autenticados |
| `/workflows/:id/edit` | `WorkflowFormPage` | ADMIN, WRITER |
| `/workflows/:workflowId/tasks/new` | `TaskFormPage` | ADMIN, WRITER |
| `/schedules` | `SchedulesPage` | Todos autenticados |
| `/profile` | `ProfilePage` | Todos autenticados |
| `/admin` | `AdminPage` | Apenas ADMIN |
| `*` | redireciona para `/dashboard` | — |

### Padrão de Componente Assíncrono

```typescript
const [loading, setLoading] = useState(false)
const [error, setError] = useState<string | null>(null)

async function handleSubmit(e: React.FormEvent) {
  e.preventDefault()
  setLoading(true)
  setError(null)
  try {
    const result = await workflowApi.create({ name })
    navigate(`/workflows/${result.id}`)
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Erro inesperado')
  } finally {
    setLoading(false)
  }
}
```

### Estratégia de Testes

Os testes de páginas utilizam Vitest 3 com Testing Library e `<MemoryRouter>`. Os testes de módulos de API verificam as chamadas `fetch` via `vi.stubGlobal`. A cobertura é medida com v8 (limiar mínimo de 80% de linhas), configurada em `vite.config.ts`.
