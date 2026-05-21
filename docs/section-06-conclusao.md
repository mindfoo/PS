# 6. CONCLUSÃO E TRABALHO FUTURO

O desenvolvimento da plataforma de automação de *workflows* permitiu consolidar competências nas áreas de arquitetura de software, desenvolvimento *full-stack* e engenharia de segurança. A solução implementada cobre os requisitos funcionais essenciais: criação e gestão de *workflows* e tarefas, execução manual e agendada, monitorização do histórico de execuções e controlo de acesso por papéis.

As principais metas técnicas foram atingidas:

- O padrão `Either<E, T>` foi adotado de forma consistente em todos os serviços (`service/utils/ServiceErrors.kt`), eliminando o uso de exceções para controlo de fluxo esperado e tornando o tratamento de erros explícito e verificável pelo compilador.
- O modelo RBAC com autoridades granulares (`workflow:read`, `workflow:write`, `workflow:delete`, `workflow:execute`) permite uma gestão de permissões flexível e desacoplada dos papéis concretos.
- A autenticação *stateless* por *cookie* HttpOnly oferece proteção XSS superior à alternativa JWT com `localStorage`.
- A cobertura de testes unitários atingiu o limiar mínimo de 80% em ambas as camadas (*backend* via JaCoCo, *frontend* via Vitest v8).

## Trabalho Futuro

Identificaram-se as seguintes áreas de melhoria e extensão para iterações futuras:

1. **Execução assíncrona distribuída** — a execução de *workflows* poderia ser delegada a uma fila de mensagens (e.g., RabbitMQ ou Kafka), permitindo processamento assíncrono e tolerância a falhas sem bloquear o servidor de aplicação.

2. **Notificações em tempo real** — a integração de WebSockets ou Server-Sent Events permitiria ao *frontend* receber atualizações de estado de execução em tempo real, sem necessidade de *polling*.

3. **Versionamento de Workflows** — suporte para múltiplas versões de um *workflow*, permitindo auditoria histórica e *rollback* para versões anteriores.

4. **Integração com serviços externos** — a configuração JSONB das tarefas (`Task.config`) foi concebida para suportar integrações com APIs externas; a implementação de conectores concretos (HTTP, scripts, chamadas SQL) tornaria a plataforma imediatamente utilizável em contextos reais.

5. **Interface de administração avançada** — expansão da `AdminPage` com métricas agregadas, gestão de papéis e visualização de auditoria de acessos.

6. **Autenticação federada** — suporte para OAuth 2.0 / OpenID Connect como alternativa ou complemento à autenticação por *cookie* nativa.

---

## 6.1 Dificuldades e Aprendizagens

### Gestão de Transações e Lazy Loading

A combinação de `FetchType.LAZY` com transações de leitura (`@Transactional(readOnly = true)`) exigiu cuidado especial na serialização de DTOs: o mapeamento entidade→DTO deve ocorrer dentro da transação ativa para evitar `LazyInitializationException`. A abordagem adotada — *extension functions* privadas de mapeamento dentro do serviço, chamadas antes do retorno do método transacional — resolveu este problema de forma elegante e encapsulada.

### Exaustividade do Either nos Controladores

A adopção do padrão `Either` revelou-se vantajosa, mas exigiu disciplina: cada novo caso de erro adicionado a uma *sealed class* obriga a atualizar todos os controladores que a utilizam, caso contrário a compilação falha. Esta característica, embora inicialmente percecionada como fricção, provou ser uma rede de segurança valiosa que preveniu erros de tratamento incompleto de falhas.

### Autenticação por Cookie com Spring Security

A configuração do `CookieAuthenticationFilter` como filtro personalizado na cadeia do Spring Security (`security/SecurityConfiguration.kt`) requereu atenção à ordem dos filtros e à correta propagação do `SecurityContextHolder`. O processamento delegado a `RequestTokenProcessor` (`security/pipeline/RequestTokenProcessor.kt`) permitiu encapsular e testar a lógica de validação de token de forma isolada.

### TypeScript Estrito no Frontend

A política de zero `any` no *frontend* obrigou ao uso sistemático de tipos genéricos e *type guards* na camada de API (`src/api/client.ts`), tornando o código mais verboso mas significativamente mais seguro. A integração de TypeScript ~6.0.2 com React 18 e Vitest 3 revelou-se estável e produtiva.

### Testes Unitários sem Contexto Spring

A estratégia de testes com MockK sem `@SpringBootTest` reduziu drasticamente os tempos de execução dos testes e eliminou a dependência de infraestrutura externa nos testes unitários. A criação manual das instâncias de controladores e serviços com dependências *mockadas* tornou os testes mais legíveis e focados no comportamento esperado.
