# 1. RESUMO

O presente relatório descreve o desenvolvimento de uma plataforma de automação de fluxos de trabalho (*workflows*), concebida no âmbito da unidade curricular de Projeto de Software. A solução foi implementada como uma aplicação *full-stack*, composta por um serviço *backend* desenvolvido em Kotlin 2.3 sobre Spring Boot 3.5, e por uma interface *frontend* construída com React 18 e TypeScript ~6.0.2.

A arquitetura adotada segue o padrão de três camadas — apresentação, lógica de negócio e persistência — com comunicação baseada em REST. O modelo de domínio engloba entidades como `Workflow`, `Task`, `Execution`, `Schedule` e `User`, geridas através de Spring Data JPA com base de dados PostgreSQL 15. A segurança é assegurada por um mecanismo de autenticação sem estado (*stateless*) baseado em *cookies* HttpOnly, em alternativa ao uso de JWT, e o controlo de acesso é implementado através de um modelo RBAC (*Role-Based Access Control*) com autoridades granulares.

A lógica de negócio nos serviços recorre ao padrão funcional `Either<E, T>`, que distingue explicitamente resultados de sucesso de falhas de domínio sem recurso a exceções. O *frontend* comunica com o *backend* exclusivamente através de um cliente HTTP centralizado, e a gestão de autenticação é encapsulada num contexto React (`AuthContext`).

O relatório documenta as decisões arquiteturais tomadas, o modelo de domínio detalhado, os mecanismos de segurança implementados e os resultados obtidos, com referência direta aos ficheiros de código-fonte relevantes.

**Palavras-chave:** automação de fluxos de trabalho, Spring Boot, Kotlin, React, TypeScript, RBAC, Either, JPA, PostgreSQL.
