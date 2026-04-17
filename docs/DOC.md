# Relatório Técnico - Plataforma de Orquestração de Workflows

## 1. Introdução

Observou-se que o projeto implementa uma API REST para gestão de utilizadores, autenticação, criação de *workflows*, definição de tarefas, configuração de agendamentos e registo de execuções. A solução encontra-se organizada segundo uma arquitetura em camadas, com separação explícita entre controladores, serviços, repositórios, entidades e DTOs, conforme a estrutura localizada em `src/main/kotlin/workflow/`.

No estado atual do repositório, a stack efetivamente utilizada é a seguinte:

| Camada | Tecnologia observada | Evidência |
|---|---|---|
| Linguagem principal | Kotlin 2.3.0 | `build.gradle.kts` |
| Runtime JVM | Java 25 | `build.gradle.kts` (`kotlin { jvmToolchain(25) }`) |
| Framework principal | Spring Boot 3.5.0 | `build.gradle.kts` |
| Persistência | Spring Data JPA + Hibernate | `build.gradle.kts`, `src/main/kotlin/workflow/repository/` |
| Base de dados | PostgreSQL 15 | `src/main/resources/application.properties`, `src/docker-compose.yml` |
| Autenticação | Cookies opacos (SHA-256) + pipeline de interceptores | `src/main/kotlin/workflow/security/` |
| Autorização | Spring Security RBAC + `@PreAuthorize` | `src/main/kotlin/workflow/security/SecurityConfiguration.kt` |
| Documentação API | springdoc OpenAPI / Swagger UI | `build.gradle.kts`, `src/main/kotlin/workflow/config/OpenApiConfig.kt` |
| Agendamento | Spring Scheduling | `src/main/kotlin/workflow/WorkflowMainApplication.kt`, `src/main/kotlin/workflow/scheduler/ScheduleDispatchJob.kt` |
| Build/empacotamento | Gradle (Kotlin DSL) + `mainClass` explícita | `build.gradle.kts` |

Importa notar duas decisões de infraestrutura transversais ao projeto: a adoção de **Java 25** como plataforma de execução, com `jvmToolchain(25)` declarado explicitamente no Gradle; e a utilização de **Spring Boot 3.5.0**, versão que introduz suporte nativo a bytecode Java 25 através da atualização do ASM embutido para a versão 9.8. Versões anteriores do Spring Boot (≤ 3.4.x) falhavam ao inspecionar classes compiladas para Java 25 (major version 69), o que tornou esta atualização de versão tecnicamente obrigatória.

---

## 2. Decisões Arquiteturais

### 2.1. Adoção de Kotlin sobre a JVM

Observou-se que a implementação foi construída integralmente em Kotlin, opção que se revela adequada para uma API com forte componente de modelação de domínio e múltiplas fronteiras entre dados internos e dados expostos externamente.

Esta escolha foi tecnicamente relevante pelas seguintes razões:

1. **Type safety e null safety** — a linguagem reduz a probabilidade de erros de referência nula, particularmente importante em relações opcionais do domínio, como em `Execution`, onde `task` e `workflow` são mutuamente contextuais (`src/main/kotlin/workflow/entity/ExecutionLog.kt`).
2. **Expressividade para DTOs** — a organização em `src/main/kotlin/workflow/dto/` evidencia a adoção de objetos de transporte específicos por caso de uso, algo que Kotlin simplifica com classes concisas e imutabilidade natural.
3. **Boa integração com Spring Boot** — os *plugins* `kotlin("plugin.spring")` e `kotlin("plugin.jpa")` observados em `build.gradle.kts` reduzem fricção na geração de *proxies*, abertura de classes e compatibilidade com JPA.

### 2.2. Spring Boot 3.5.0 como núcleo de composição

A adoção de Spring Boot 3.5.0 permitiu consolidar, numa única plataforma, responsabilidades de HTTP, validação, segurança, persistência, serialização JSON, documentação OpenAPI e agendamento. Esta opção foi preferida em detrimento de uma composição mais manual de bibliotecas, porque:

- reduziu o custo de integração entre módulos;
- favoreceu convenção sobre configuração;
- permitiu concentrar o esforço no domínio funcional dos *workflows* em vez de na infraestrutura;
- facilitou a aplicação do **Princípio da Responsabilidade Única** por classe, sem sacrificar produtividade.

O arranque da aplicação encontra-se em `src/main/kotlin/workflow/WorkflowMainApplication.kt`, onde se observou a ativação explícita de `@EnableScheduling`. Esta anotação sinaliza que o sistema não se limita a CRUD tradicional, tendo sido concebido para incorporar comportamento temporal e despacho periódico.

### 2.3. Arquitetura em camadas e separação de responsabilidades

Observou-se a aplicação consistente do fluxo **Controller → Service → Repository**, conforme preconizado pelos princípios de arquitetura em camadas.

| Camada | Responsabilidade | Exemplos |
|---|---|---|
| Controller | Exposição HTTP, validação de entrada, mapeamento de respostas | `WorkflowController.kt`, `TaskController.kt`, `ScheduleController.kt`, `AuthController.kt` |
| Service | Regras de negócio, autorização contextual por posse, orquestração entre repositórios | `WorkflowService.kt`, `TaskService.kt`, `ScheduleService.kt`, `ExecutionService.kt`, `AuthService.kt` |
| Repository | Acesso a dados e *queries* especializadas | `UserRepository.kt`, `WorkflowRepository.kt`, `ScheduleRepository.kt`, `UserTokenRepository.kt` |
| Entity | Modelo persistente relacional | `src/main/kotlin/workflow/entity/` |
| DTO / Utils | Fronteira externa e convenções transversais | `src/main/kotlin/workflow/dto/`, `src/main/kotlin/workflow/utils/` |

Esta decomposição foi preferida porque aumenta testabilidade, reduz acoplamento entre transporte HTTP e persistência e permite evoluir o sistema sem reescrever transversalmente todos os componentes.

### 2.4. Decisão por DTOs em vez de exposição direta de entidades

Observou-se que os controladores devolvem DTOs ou envelopes de resposta, nunca entidades JPA diretamente. Por exemplo, `WorkflowController` e `TaskController` convertem o resultado dos serviços em `WorkflowResponse` e `TaskResponse`, enquanto os serviços encapsulam a lógica de mapeamento.

Esta abordagem foi escolhida por três motivos centrais:

- evita fuga acidental de atributos internos ou sensíveis;
- impede serialização recursiva de grafos JPA com relações *lazy*;
- estabiliza o contrato público da API mesmo quando o modelo interno evolui.

Em contexto académico e profissional, esta decisão reforça a separação entre **modelo de persistência** e **modelo de integração**, o que constitui boa prática de engenharia de APIs.

### 2.5. Padronização de rotas e centralização de URIs

Em `src/main/kotlin/workflow/utils/Uris.kt`, foi observada a centralização dos caminhos da API numa estrutura de objetos aninhados (`Auth`, `Workflows`, `Tasks`, `Schedules`). Em vez de repetir *strings* em cada controlador, as rotas existem num único ponto de verdade, o que reduz erros de digitação e simplifica refatorações.

### 2.6. Tratamento funcional de resultados com `Either`

Observou-se que a lógica de aplicação evita exceções para cenários de negócio previsíveis, usando o tipo selado `Either` definido em `src/main/kotlin/workflow/utils/Either.kt` e hierarquias de erro tipadas em `src/main/kotlin/workflow/service/utils/ServiceErrors.kt`.

Esta decisão foi tecnicamente acertada porque o compilador força tratamento exaustivo de todos os casos de falha nos `when` dos controladores, tornando o erro de negócio um valor tipado em vez de uma exceção implícita:

```kotlin
when (val result = workflowService.getById(id, authentication.name)) {
    is Success -> ResponseEntity.ok(result.value)
    is Failure -> when (result.value) {
        WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
        WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
    }
}
```

### 2.7. Respostas de erro normalizadas com RFC 7807

Em `src/main/kotlin/workflow/utils/Problem.kt` e `src/main/kotlin/workflow/config/GlobalExceptionHandler.kt`, observou-se a adoção do formato `application/problem+json`. Implementou-se uma estratégia híbrida: erros de negócio previsíveis são tratados por `Either`, enquanto falhas técnicas, validações e exceções imprevistas são convertidas centralmente pelo `@RestControllerAdvice`. Esta composição distingue claramente **controlo de fluxo de domínio** de **falhas infraestruturais ou excecionais**.

---

## 3. Camada de Persistência: JPA vs JDBI

### 3.1. Enquadramento da decisão

O repositório inclui referências a uma solução comparativa baseada em módulos JDBI no diretório `ref/`, mas a implementação efetiva da API corrente utiliza **Spring Data JPA/Hibernate**. A escolha atual deve ser entendida como uma decisão deliberada de favorecer rapidez de modelação do domínio e integração nativa com Spring.

### 3.2. Comparação técnica

| Critério | JPA / Hibernate | JDBI |
|---|---|---|
| Abstração de relações | Elevada; navegação objeto-relacional automática | Baixa; relações tratadas manualmente |
| Produtividade inicial | Muito elevada | Moderada |
| Controlo fino sobre SQL | Médio | Elevado |
| *Boilerplate* | Reduzido | Maior |
| Integração com Spring Data | Nativa | Parcial / manual |
| Adequação ao domínio atual | Muito adequada | Mais indicada para consulta intensiva e SQL altamente otimizado |

### 3.3. Justificação da escolha atual por JPA

Observou-se um domínio com várias relações significativas:

- `User` → `Roles` (`src/main/kotlin/workflow/entity/User.kt`)
- `Roles` ↔ `Permission` (`src/main/kotlin/workflow/entity/Roles.kt`)
- `Workflow` → `User` (`src/main/kotlin/workflow/entity/Workflow.kt`)
- `Task` → `Workflow` (`src/main/kotlin/workflow/entity/Task.kt`)
- `Schedule` → `Workflow` e `User` (`src/main/kotlin/workflow/entity/Schedule.kt`)
- `Execution` → `User`, `Task` e `Workflow` (`src/main/kotlin/workflow/entity/ExecutionLog.kt`)
- `UserToken` → `User` (`src/main/kotlin/workflow/entity/UserToken.kt`)

Num contexto destes, a JPA foi uma escolha racional porque reduz substancialmente o esforço de mapeamento e permite que a lógica de negócio permaneça centrada no domínio, não na reconstrução manual de objetos a partir de `ResultSet`.

### 3.4. Mitigação das limitações da JPA

Foi igualmente observado que a JPA não foi utilizada de forma ingénua. Surgem mecanismos específicos para mitigar os seus problemas clássicos.

#### a) `JOIN FETCH` para evitar inicialização *lazy* fora de contexto transacional

Em `src/main/kotlin/workflow/repository/UserRepository.kt`, a *query* `findByUsernameWithPermissions` utiliza `JOIN FETCH` para carregar `user → role → permissions` numa única instrução SQL. O mesmo padrão foi aplicado em `UserTokenRepository.findByTokenHashWithUser`, necessário para que o filtro de autenticação por cookie possa carregar o utilizador e as suas permissões numa única operação durante a verificação do token.

#### b) `@Transactional` com semântica explícita

Em `src/main/kotlin/workflow/security/CustomUserDetailsService.kt`, observou-se `@Transactional(readOnly = true)` no carregamento do utilizador. Nos serviços de escrita, como `AuthService`, `WorkflowService` e `ScheduleService`, as transações anotam explicitamente os métodos que modificam estado.

#### c) Uso de `JSONB` para dados flexíveis

Em `Task` e `Execution`, campos como `config` e `output` foram mapeados com `@JdbcTypeCode(SqlTypes.JSON)` e `columnDefinition = "jsonb"`. Esta escolha é particularmente acertada em sistemas de orquestração, onde a estrutura de configuração de uma tarefa pode variar por tipo sem justificar multiplicação de tabelas normalizadas.

### 3.5. Auditoria temporal comum

Todas as entidades relevantes herdam de `Timestamp` (`src/main/kotlin/workflow/entity/Timestamp.kt`), que persiste automaticamente `createdAt` e `lastUpdated` via `@CreationTimestamp` e `@UpdateTimestamp` do Hibernate.

---

## 4. Segurança e Autenticação

### 4.1. Evolução do modelo de autenticação: de JWT para tokens opacos por cookie

A implementação plataforma recorreu a **tokens opacos armazenados na base de dados e transportados por cookie HTTP**.

Esta mudança arquitetural envolveu as seguintes alterações:

| Componente | Antes (JWT) | Depois (Cookie opaco) |
|---|---|---|
| Formato do token | String aleatória Base64Url (256 bits) |
| Armazenamento | Hash SHA-256 em `user_tokens` (DB) |
| Transporte | Cookie `HttpOnly` |
| Revogação | Instantânea (delete do hash na DB) |
| Biblioteca | Java stdlib (`SecureRandom`, `MessageDigest`) |

### 4.2. Geração e validação do token — `TokenUtils` e `UserToken`

Observou-se que a geração e validação de tokens foi implementada em `src/main/kotlin/workflow/security/TokenUtils.kt`:

```kotlin
object TokenUtils {
    fun generateToken(): String =
        ByteArray(32).also { SecureRandom.getInstanceStrong().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(digest.digest(token.toByteArray(Charsets.UTF_8)))
    }
}
```

O token em claro (256 bits aleatórios) **nunca é armazenado** — apenas o seu hash SHA-256 persiste na tabela `user_tokens` (`src/main/kotlin/workflow/entity/UserToken.kt`). O valor em claro reside exclusivamente no cookie do cliente. Esta decisão segue o princípio de minimização de exposição de segredos na base de dados.

### 4.3. Pipeline de autenticação por cookie — espelho do projeto de referência

A estrutura de autenticação foi implamentada no pacote `src/main/kotlin/workflow/security/pipeline/` contém os três componentes:

#### `RequestTokenProcessor`

Lê o valor do cookie, computa o hash SHA-256, pesquisa em `UserTokenRepository`, valida a expiração e devolve um `AuthenticatedUser` ou `null`.

#### `AuthenticationInterceptor`

`HandlerInterceptor` que intercepta cada pedido antes de invocar o *handler*. Se o método destino declarar um parâmetro do tipo `AuthenticatedUser`, o interceptor obriga à presença do cookie `token` válido — caso contrário responde com `401`. Este padrão é uma tradução direta do `AuthenticationInterceptor`:

```kotlin
if (handler is HandlerMethod &&
    handler.methodParameters.any { it.parameterType == AuthenticatedUser::class.java }
) {
    val user = requestTokenProcessor.processAuthorizationCookieValue(
        request.cookies?.find { it.name == COOKIE_NAME }?.value
    )
    return if (user == null) { response.status = 401; false }
    else { AuthenticatedUserArgumentResolver.addUserTo(user, request); true }
}
```

#### `AuthenticatedUserArgumentResolver`

`HandlerMethodArgumentResolver` que resolve o parâmetro `AuthenticatedUser` a partir do atributo de pedido definido pelo interceptor. Qualquer *handler method* pode assim declarar `fun endpoint(user: AuthenticatedUser)` sem lógica adicional.

Os três componentes são registados na `SecurityConfig`, que implementa `WebMvcConfigurer` para adicionar o interceptor e o argument resolver:

```kotlin
override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(authenticationInterceptor)
}
override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
    resolvers.add(authenticatedUserArgumentResolver)
}
```

### 4.4. Integração com Spring Security: `CookieAuthenticationFilter`

O projeto mantém `@PreAuthorize("hasAuthority('workflow:read')")` em todos os endpoints, o que requer que o `SecurityContextHolder` esteja populado antes da invocação do método. Como o `AuthenticationInterceptor` corre *depois* do filtro de segurança, foi adicionado `CookieAuthenticationFilter` (`src/main/kotlin/workflow/security/CookieAuthenticationFilter.kt`) — um `OncePerRequestFilter` que lê o cookie `token`, valida contra a DB e popula o `SecurityContext`, complementando o interceptor:

```
Pedido HTTP
    → CookieAuthenticationFilter (popula SecurityContextHolder para @PreAuthorize)
    → AuthenticationInterceptor  (injeta AuthenticatedUser nos parâmetros do handler)
    → Handler Method
```

### 4.5. Validação de credenciais sem `DaoAuthenticationProvider`

A validação da palavra-passe é efetuada diretamente em `AuthService.login` com `passwordEncoder.matches(rawPassword, user.passwordValidation)`:

```kotlin
val user = userRepository.findByUsername(request.username)
    ?: return failure(AuthError.InvalidCredentials)

if (!passwordEncoder.matches(request.password, user.passwordValidation)) {
    return failure(AuthError.InvalidCredentials)
}
```

Esta abordagem é preferível porque elimina a dependência de uma abstração Spring Security (o `AuthenticationManager`) para uma operação puramente de domínio — a comparação de uma palavra-passe com o seu hash BCrypt.

### 4.6. Comportamento dos cookies de autenticação

O `AuthController` (`src/main/kotlin/workflow/controller/AuthController.kt`) emite dois cookies na resposta ao *login*:

| Cookie | `HttpOnly` | Propósito |
|---|---|---|
| `token` | `true` | Valor opaco de autenticação; inacessível via JavaScript (proteção XSS) |
| `username` | `false` | Identificador legível pelo *frontend* para fins de apresentação |

Ambos os cookies são configurados com `SameSite=Strict` (proteção CSRF) e `Path=/`. O atributo `Secure` está configurado como `false` para o ambiente de desenvolvimento local (HTTP); em produção (HTTPS) deverá ser ativado através da constante `SECURE_COOKIES` presente no controlador.

No *logout*, os mesmos cookies são emitidos com `maxAge=0` (expiração imediata) e o hash do token é eliminado da base de dados, tornando o token irrevogavelmente inválido.

### 4.7. Modelo de permissões atómicas (RBAC)

O modelo de autorização não depende exclusivamente de papéis genéricos. Implementou-se um sistema de permissões compostas por **recurso + ação**, definido em:

- `src/main/kotlin/workflow/entity/enums/ResourceType.kt` — `WORKFLOW`, `TASK`, `USER`, `SCHEDULE`, `EXECUTION`
- `src/main/kotlin/workflow/entity/enums/ActionType.kt` — `READ`, `WRITE`, `DELETE`, `EXECUTE`, `MANAGE`
- `src/main/kotlin/workflow/entity/Permission.kt` — campo calculado `slug` que produz `workflow:read`, `task:delete`, etc.

Os papéis (`READER`, `WRITER`, `DEV`, `ADMIN`) agregam conjuntos distintos de permissões (N:M), criados automaticamente por `DataInitializer` no arranque da aplicação. Na fase de autenticação, `CustomUserDetailsService` transforma o papel e as permissões em `GrantedAuthority`, permitindo que `@PreAuthorize("hasAuthority('workflow:execute')")` nos controladores verifique a permissão exata sem acoplar código ao nome do papel.

| Endpoint | Autoridade exigida | Ficheiro |
|---|---|---|
| Listar workflows | `workflow:read` | `WorkflowController.kt` |
| Criar tarefa | `task:write` | `TaskController.kt` |
| Eliminar agendamento | `schedule:delete` | `ScheduleController.kt` |
| Executar workflow manualmente | `workflow:execute` | `WorkflowController.kt` |

---

## 5. Dificuldades e Aprendizagens


### 5.1. Separação entre erros de domínio e falhas técnicas

A convivência entre `Either` (erros de negócio tipados) e `GlobalExceptionHandler` (falhas técnicas) demonstra que nem todos os erros devem ser tratados da mesma forma. Erros previsíveis — utilizador inexistente, cron inválido, credenciais incorretas — são valores tipados; falhas inesperadas são exceções.

### 5.3. Modelação de workflows com dados parcialmente estruturados

O uso de `JSONB` para `Task.config` e `Execution.output` representa uma aprendizagem relevante: em sistemas de orquestração, a flexibilidade de esquema em campos operacionais evita proliferação desnecessária de colunas e migrações frequentes de esquema relacional.

### 5.4. Concorrência em execução agendada

Em `src/main/kotlin/workflow/repository/ScheduleRepository.kt`, observou-se `@Lock(LockModeType.PESSIMISTIC_WRITE)` na pesquisa de agendamentos devidos. Esta decisão evidencia compreensão de que sistemas temporais exigem exclusão mútua sobre o trabalho elegível para evitar dupla execução.

### 5.5. Compatibilidade entre Java 25 e Spring Boot

A compilação com `jvmToolchain(25)` produzia bytecode com *major version* 69, que o ASM embutido no Spring Boot 3.4.x não suportava. A resolução passou por duas ações complementares: a atualização para Spring Boot 3.5.0 (que inclui ASM 9.8, com suporte a Java 25) e a declaração explícita da classe principal no Gradle para evitar a deteção automática que também falhava com bytecode Java 25.

---

## 6. Conclusão

Concluiu-se que a implementação atual da **Workflow Platform** apresenta uma base arquitetural sólida, coerente com os objetivos de uma API de orquestração académica e suficientemente estruturada para suportar evolução incremental.

As decisões mais relevantes observadas foram as seguintes:

1. adoção de Kotlin 2.3.0 e Spring Boot 3.5.0 sobre Java 25;
2. utilização de JPA para modelação de um domínio relacionalmente rico;
4. implementação de autenticação **stateless por cookie** com tokens opacos armazenados na DB (SHA-256);
5. pipeline de autenticação com `AuthenticationInterceptor` + `AuthenticatedUserArgumentResolver`;
6. RBAC granular baseado em permissões atómicas `recurso:ação`, verificadas por `@PreAuthorize`;
7. uso de `Either` e `Problem Details` (RFC 7807) para clareza semântica das respostas de erro;
8. agendamento periódico com proteção pessimista (`PESSIMISTIC_WRITE`) contra concorrência indevida.

Do ponto de vista de escalabilidade conceptual, os próximos passos naturais incluem:

- execução assíncrona real das tarefas individuais de um workflow;
- políticas explícitas de *retry* por tarefa, em vez de apenas contagem em `Execution.retryCount`;
- alertas automáticos acionados por falha de execução, via a entidade `Alert` já modelada;
- auditoria detalhada do encadeamento entre `WorkflowTaskOrder` e execuções efetivas;
- introdução de mensageria ou *job queues* quando o volume operacional superar a capacidade do despacho síncrono.

Em síntese, a implementação atual demonstra compreensão correta de conceitos como **Princípio da Responsabilidade Única**, **arquitetura stateless**, **type safety**, **separação entre modelo interno e contrato externo** e **segurança por cookie com revogação serverside**. O projeto encontra-se, por isso, numa fase tecnicamente consistente para continuação do desenvolvimento e para fundamentação académica das decisões tomadas.
