# 6. CONCLUSÃO E TRABALHO FUTURO (capitulo 4)
O desenvolvimento da plataforma de automação de workflows permitiu consolidar competências nas áreas de arquitetura de software e desenvolvimento full-stack. A solução implementada cobre os requisitos funcionais essenciais: criação e gestão de workflows e tarefas, execução manual e agendada, monitorização do histórico de execuções e controlo de acesso por roles.

As principais metas técnicas foram atingidas:

- O modelo RBAC com autoridades granulares permite uma gestão de permissões flexível e desacoplada dos roles, authorities e resources.
- A autenticação stateless por cookie HttpOnly.
- A cobertura de testes unitários sobre a implementação.

4.1 Trabalho Futuro

Identificaram-se as seguintes áreas de melhoria e extensão para iterações futuras:

1. Execução assíncrona distribuída — a execução de workflows poderia ser delegada a uma fila de mensagens (e.g., RabbitMQ ou Kafka), permitindo processamento assíncrono e tolerância a falhas sem bloquear o servidor de aplicação.

3. Versionamento de Workflows — suporte para múltiplas versões de um workflow permitindo auditoria histórica e rollback para versões anteriores.

4. Integração com serviços externos — a configuração JSONB das tarefas foi concebida para suportar integrações com APIs externas; a implementação de conectores concretos (HTTP, scripts, chamadas SQL) tornaria a plataforma imediatamente utilizável em contextos reais.

5. Interface de administração avançada — expansão da `AdminPage` com métricas agregadas, gestão de papéis e visualização de auditoria de acessos.
   4.2 Dificuldades e Aprendizagens

4.2.1 Gestão de Transações e Lazy Loading

A combinação de `FetchType.LAZY` com transações de leitura (`@Transactional(readOnly = true)`) exigiu cuidado especial na serialização de DTOs: o mapeamento entidade→DTO deve ocorrer dentro da transação ativa para evitar `LazyInitializationException`.

4.2.2 Exaustividade do Either nos Controladores

A adopção do padrão `Either` revelou-se vantajosa, mas exigiu que cada novo caso de erro adicionado a uma *sealed class* obriga a atualizar todos os controladores que a utilizam, caso contrário a compilação falha. Esta característica provou ser uma rede de segurança que preveniu erros de tratamento incompleto de falhas.

4.2.3 Autenticação por Cookie com Spring Security

A configuração do `CookieAuthenticationFilter` como filtro personalizado na cadeia do Spring Security (`security/SecurityConfiguration.kt`) precisou de atenção à ordem dos filtros e à correta propagação do `SecurityContextHolder`. A validação de segurança foi feito no `RequestTokenProcessor` (`security/pipeline/RequestTokenProcessor.kt`) permitindo testar a lógica de validação de token de forma isolada.

4.2.4 Virtual Threads e gestão de concorrência
A utilização de virtual threads (Project Loom) no backend permitiu uma abordagem mais simples e eficiente para lidar com a concorrência, especialmente na execução de workflows que podem envolver múltiplas tarefas paralelas. A capacidade de criar milhares de threads leves sem o overhead tradicional de threads do sistema operacional facilitou a implementação de execuções assíncronas e escaláveis.

4.2.5 LISTEN/NOTIFY do Postgres e SSEs
A implementação de notificações em tempo real para a monitorização do histórico de execuções foi um desafio interessante. A utilização do mecanismo `LISTEN/NOTIFY` do PostgreSQL, combinado com Server-Sent Events (SSE) no frontend, permitiu uma solução eficiente e reativa para atualizar a interface do utilizador em tempo real sem necessidade de polling constante.
