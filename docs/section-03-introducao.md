Introdução (capitulo 1)

A automação de fluxos de trabalho é uma prática amplamente adotada em contextos empresariais e de engenharia de software, permitindo a orquestração de tarefas sequenciais ou paralelas de forma controlada, auditável e escalável. A necessidade de gerir processos complexos com múltiplas dependências, diferentes intervenientes e requisitos de rastreabilidade motivou o desenvolvimento de plataformas dedicadas à definição, execução e monitorização de workflows.

O presente projeto insere-se neste domínio, propondo a construção de uma plataforma full-stack para automação de fluxos de trabalho. A solução permite que utilizadores autenticados definam workflows compostos por tarefas (tasks) configuráveis, estabeleçam ordens de execução com suporte a paralelismo, agendamento e acompanhem o histórico de execuções em tempo real.

1.1.	Objetivos

Os objetivos principais do projeto são:

1.	Desenvolver uma API REST robusta e documentada com Spring Boot e Kotlin, seguindo boas práticas de arquitetura em camadas.
2.	Implementar um modelo de controlo de acesso baseado em papéis (RBAC) com autoridades granulares, assegurando que cada utilizador apenas acede aos recursos para os quais possui permissão.
3.	Construir uma interface frontend com React e TypeScript, com gestão centralizada de autenticação e permissões.
4.	Garantir cobertura de testes tanto no backend (JaCoCo) como no frontend (Vitest).


1.2.	Contexto Tecnológico

A escolha de Kotlin 2.3 em detrimento do Java puro foi motivada pela total interoperabilidade com o ecossistema JVM. Importa salientar que a plataforma assenta sobre o Java 25, tirando partido das mais recentes inovações da especificação da linguagem, em contraste com a versão 21 utilizada predominantemente ao longo do percurso académico. O Spring Boot 3.5 foi selecionado pela integração nativa com Spring Data JPA e Spring Security.

No frontend, React 18 com TypeScript 6.0.2 foi preferido pela possibilidade de tipagem e pela capacidade de compor interfaces reativas de forma declarativa.
