Solução Proposta (capitulo 2)


2.1	Decisões Arquiteturais

2.1.1 Kotlin e Gradle

Optou-se pelo Kotlin 2.3 pela total interoperabilidade com o ecossistema JVM (Java Virtual Machine) e com o framework Spring, o que assegurou que nenhuma funcionalidade ficasse comprometida.

A escolha do Gradle como ferramenta de automação de compilação (build automation) deveu-se ao facto de este ser o padrão consolidado da indústria (industry standard) para o ecossistema Kotlin. Ao contrário de alternativas como o Apache Maven, o Gradle oferece suporte nativo e otimizado para o Kotlin, permitindo que os scripts de configuração (build.gradle.kts) sejam escritos na mesma linguagem da aplicação. Isto resulta num processo de compilação mais eficiente através de builds incrementais e num controlo estrito de dependências.

O ficheiro “code/jvm/build.gradle.kts“ configura o compilador Kotlin com o plugin Spring (kotlin("plugin.spring")) e o plugin JPA (kotlin("plugin.jpa")), ambos necessários para a geração automática de anotações requeridas pelo Hibernate.

2.2.2 Spring Boot 3.5

O Spring Boot 3.5 foi selecionado pela sua maturidade, pela integração nativa com Spring Data JPA, Spring Security e Spring Scheduler, e pela produtividade que oferece no desenvolvimento de APIs REST.

A versão 3.5 assegura compatibilidade total com o Java 25. A adoção do Java 25 — a versão de suporte de longo prazo (Long-Term Support — LTS) mais recente da plataforma — foi motivada pela necessidade de maximizar a escalabilidade da aplicação através do uso estável de Virtual Threads. Adicionalmente, a utilização do Kotlin 2.3 revelou-se estritamente necessária por ser a primeira versão da linguagem a trazer suporte nativo e otimizações de compilação (através do novo compilador K2) totalmente alinhadas com as especificações do bytecode e da API do Java 25, garantindo estabilidade e performance na execução de concorrência ligeira.

2.1.3 Autenticação por Cookie sobre JWT

Optou-se por um modelo de autenticação sem estado (stateless) baseado em cookies HttpOnly, em alternativa à abordagem tradicional com JSON Web Tokens (JWT).

A motivação principal para esta decisão prendeu-se com a mitigação robusta de ataques de XSS (Cross-Site Scripting). Ao contrário de tokens JWT que são habitualmente armazenados no localStorage ou sessionStorage do frontend — ficando vulneráveis a scripts maliciosos injetados no navegador —, os cookies configurados com o atributo HttpOnly são completamente inacessíveis via JavaScript. O browser anexa automaticamente o cookie a cada requisição HTTP direcionada à API, protegendo a sessão do utilizador de roubos de identidade a partir do cliente.

Em adicional, para mitigar o risco de comprometimento da persistência, o token opaco é armazenado na tabela user_tokens sob a forma de um hash utilizando o algoritmo SHA-256. Esta abordagem garante que, mesmo em caso de acesso não autorizado à base de dados, os segredos em texto claro nunca sejam expostos.

2.1.4 Padrão Either para Gestão de Erros

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

2.1.5 RBAC com Autoridades Granulares

O controlo de acesso é implementado através de autoridades granulares do formato `recurso:ação` (e.g., `workflow:read`, `workflow:write`), em vez de verificações de papel (*role*) diretas. Esta abordagem desacopla a lógica de autorização dos papéis concretos, tornando a adição de novos papéis transparente para os controladores. O mapeamento de papéis para autoridades é definido em `code/jvm/src/main/kotlin/workflow/config/DataInitializer.kt`.


2.2	Estrutura do Projeto

O projeto é um monorepo com dois módulos principais: “js” dedicado ao código de frontend e “jvm” dedicado ao código de backend.

<inserir pic estrutura>


2.2.1 Modelo de Domínio em Detalhe

O modelo de domínio é composto pelas seguintes entidades JPA, todas definidas em `code/jvm/src/main/kotlin/workflow/entity/`:

Entidade	Tabela	Descrição
`User`	`users`	Utilizador autenticado;
`Roles`	`roles`	Papel do utilizador: `ADMIN`, `WRITER`, `READER`, `DEV`
`Permission`	`permissions`	Par `ResourceType` × `ActionType`; atribuído a `Roles`
`Workflow`	`workflows`	Fluxo de trabalho; pertence a `User`; ADMIN vê todos
`Task`	`tasks`	Tarefa configurável; campo `config` é JSONB
`WorkflowTaskOrder`	`workflow_tasks_order`	Liga `Workflow` a `Task`; `taskOrder`
`Execution`	`executions`	Execução de `Workflow`; `status`: PENDING→RUNNING→SUCCESS/ERROR; `output` é JSONB
`Schedule`	`schedules`	Agendamento com expressão cron; ativado por `ScheduleDispatchJob`
`UserToken`	`user_tokens`	Token de sessão opaco, armazenado com hash SHA-256


Todas as entidades estendem `Timestamp`. As relações utilizam `FetchType.LAZY` para evitar *N+1 queries* involuntárias. Os campos JSONB (`Task.config`, `Execution.output`) utilizam a anotação `@JdbcTypeCode(SqlTypes.JSON)`.

A entidade `WorkflowTaskOrder` suporta execução paralela: tarefas com o mesmo valor de `taskOrder` são executadas em paralelo; valores distintos definem ordem sequencial.

Os enumerados `ResourceType` e `ActionType`, definidos em `entity/enums/`, representam os recursos e ações possíveis para o sistema de permissões.


2.3	Segurança e RBAC

2.3.1 Modelo de Autenticação

A autenticação é **sem estado e baseada em *cookie*** — não é utilizado JWT. O fluxo completo é:
1. `POST /api/auth/login` → `AuthService` valida as credenciais → gera token opaco → persiste `UserToken` com *hash* SHA-256 → define *cookie* `token` com atributos `HttpOnly` e `SameSite=Strict`.
2. Em cada pedido subsequente: `CookieAuthenticationFilter` (`security/CookieAuthenticationFilter.kt`) lê o *cookie* `token` → `RequestTokenProcessor` (`security/pipeline/RequestTokenProcessor.kt`) valida contra a base de dados → `CustomUserDetailsService` carrega o utilizador → popula o `SecurityContextHolder`.
3. As anotações `@PreAuthorize` funcionam porque o `SecurityContextHolder` está populado antes de cada invocação do controlador.
4. `POST /api/auth/logout` elimina a linha `UserToken`, invalidando a sessão.

Os *endpoints* públicos (sem autenticação): `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, conforme configurado em `security/SecurityConfiguration.kt`.

2.3.2 Modelo RBAC

Os papéis disponíveis são `ADMIN`, `WRITER`, `READER` e `DEV`, cada um com um conjunto de autoridades configurado em `config/DataInitializer.kt`. Existem ainda vários resources disponíveis para interação: “workflows”, “tasks”, “executions” e“users”.

Papel	Autoridades
`ADMIN`	Todas as autoridades; lê, edita e gere todos os resources disponíveis
`WRITER`	`workflow:read`, `workflow:write`, `workflow:delete`, `workflow:execute` e herda todas as permissões de leitura.
`READER`	``<allResources>:read`
`DEV`	`workflow:write`,  `task:write`, e herda todas as permissões de leitura.

As permissões disponíveis para o resource “workflows” são:

Autoridade	Utilização
`workflow:read`	GET workflows
`workflow:write`	POST/PUT/PATCH workflows,
`workflow:delete`	DELETE workflows
`workflow:execute`	POST `/run`, POST `/cancel`


2.3.3 `@PreAuthorize` — Mecanismo de permissão por anotações

A anotação `@PreAuthorize` é processada pelo Spring Security através de Spring AOP, antes da invocação do método anotado. A expressão `hasAuthority('workflow:read')` é avaliada contra a coleção de `GrantedAuthority` do `Authentication` presente no `SecurityContextHolder`. O processamento ocorre na cadeia de filtros antes de qualquer lógica de controlador.

Exemplo de utilização nos controladores (`controller/WorkflowController.kt`):

@PreAuthorize("hasAuthority('workflow:read')")

2.4	Lógica de Negócio — Camada de Serviço

2.4.1 Padrão Either

Cada serviço retorna `Either<XxxError, T>`. As classes de erro são *sealed classes* definidas em `service/utils/ServiceErrors.kt`.
O compilador Kotlin garante exaustividade nas expressões `when` nos controladores: caso seja adicionado um novo caso de erro sem correspondente no controlador, a compilação falha. Este mecanismo elimina silenciosamente os erros não tratados.

2.4.2 Serviços Disponíveis

Serviço	Ficheiro	Responsabilidade
`AuthService`	`service/AuthService.kt`	Registo, login, logout, perfil
`WorkflowService`	`service/WorkflowService.kt`	CRUD de workflows, ligação de tarefas, execução
`TaskService`	`service/TaskService.kt`	CRUD de tarefas, execução isolada
`UserService	`service/UserService.kt`	Gestão de utilizadores, atribuição de papéis
`ExecutionService`	`service/ExecutionService.kt`	Consulta e cancelamento de execuções
`ScheduleService`	`service/ScheduleService.kt`	CRUD de agendamentos, validação de cron
`ServiceHelpers`	`service/ServiceHelpers.kt`	Utilitários partilhados (e.g., `findUser`)


2.5	Camada de Apresentação — Controllers e DTOs

2.5.1 Controllers

Os cinco controladores REST (`controller/`) seguem um padrão uniforme:

- Anotados com `@RestController`, `@Tag` (OpenAPI) e `@RequestMapping`.
- Cada endpoint tem `@Operation` e `@ApiResponses`, com respostas de erro a utilizar `mediaType = Problem.MEDIA_TYPE`.
- Delegam toda a lógica ao serviço correspondente e mapeiam o resultado `Either` para `ResponseEntity`.

2.5.2 Problem RFC 7807

Todos os erros seguem o formato RFC 7807 (`application/problem+json`), através de `Problem.response(status, Problem.xxx)` definido em `utils/Problem.kt`.

2.5.3 Data Treansfer Objects (DTO)

Os DTOs de pedido são `data class` com anotações de validação (`@field:NotBlank`, `@field:Size`). Os DTOs de resposta são `data class` com campos `val`. O mapeamento entidade→DTO é realizado através de funções de extensão privadas dentro do serviço correspondente.

2.5.4 Rotas

Todas as constantes de rota estão definidas em `utils/Uris.kt`. Nunca se utilizam “lose strings” de caminho codificadas diretamente nas anotações de mapeamento.
