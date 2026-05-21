# 3. INTRODUÇÃO

A automação de fluxos de trabalho é uma prática amplamente adotada em contextos empresariais e de engenharia de software, permitindo a orquestração de tarefas sequenciais ou paralelas de forma controlada, auditável e escalável. A necessidade de gerir processos complexos com múltiplas dependências, diferentes atores e requisitos de rastreabilidade motivou o desenvolvimento de plataformas dedicadas à definição, execução e monitorização de *workflows*.

O presente projeto insere-se neste domínio, propondo a construção de uma plataforma *full-stack* para automação de fluxos de trabalho. A solução permite que utilizadores autenticados definam *workflows* compostos por tarefas configuráveis, estabeleçam ordens de execução com suporte a paralelismo, agendamento automático via expressões *cron*, e acompanhem o histórico de execuções em tempo real.

## Objetivos

Os objetivos principais do projeto são:

1. Desenvolver uma API REST robusta e documentada com Spring Boot 3.5 e Kotlin 2.3, seguindo boas práticas de arquitetura em camadas.
2. Implementar um modelo de controlo de acesso baseado em papéis (RBAC) com autoridades granulares, assegurando que cada utilizador apenas acede aos recursos para os quais possui permissão.
3. Adotar o padrão funcional `Either<E, T>` para tratamento explícito de erros de domínio nos serviços, eliminando o uso de exceções para fluxos de controlo esperados.
4. Construir uma interface *frontend* com React 18 e TypeScript, com gestão centralizada de autenticação e permissões.
5. Garantir cobertura de testes unitários com uma taxa mínima de 80%, tanto no *backend* (JaCoCo) como no *frontend* (Vitest v8).

## Estrutura do Relatório

O relatório encontra-se organizado da seguinte forma:

- **Secção 4** apresenta a solução proposta, incluindo as decisões arquiteturais, o modelo de domínio, os mecanismos de segurança e a lógica de negócio.
- **Secção 5** detalha a implementação de infraestrutura, cobrindo a camada de persistência, as entidades JPA, o *backend* Spring Boot e o *frontend* React.
- **Secção 6** apresenta as conclusões, dificuldades encontradas e trabalho futuro.

## Contexto Tecnológico

A escolha de Kotlin 2.3 em detrimento do Java puro foi motivada pela segurança em relação a valores nulos (*null safety*), pela expressividade sintática que reduz o código *boilerplate*, e pela total interoperabilidade com o ecossistema JVM. O Spring Boot 3.5 foi selecionado pela maturidade, pela integração nativa com Spring Data JPA e Spring Security, e pela produtividade que oferece no desenvolvimento de APIs REST.

No *frontend*, React 18 com TypeScript ~6.0.2 foi preferido pela tipagem estática, pelo ecossistema de testes maduro (Vitest 3.1.3, Testing Library) e pela capacidade de compor interfaces reativas de forma declarativa.
