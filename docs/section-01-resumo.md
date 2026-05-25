# 1. RESUMO

O presente relatório descreve o desenvolvimento de uma plataforma de automação de processos (“workflows”), concebida no âmbito da unidade curricular de Projeto e Seminário. A solução foi implementada como uma aplicação full-stack, composta por um serviço backend desenvolvido em Kotlin 2.3 sobre Spring Boot 3.5, e por uma interface frontend construída com React 18 e TypeScript 6.0.2.
A arquitetura adotada segue um padrão com três camadas: apresentação, lógica de negócio e persistência — com comunicação baseada em REST. O modelo de domínio engloba entidades como: `Workflow`, `Task`, `Execution`, `Schedule` e `User`, geridas através de Spring Data JPA com uma base de dados PostgreSQL 15.
A segurança da API é assegurada por um mecanismo de autenticação sem estado (stateless) baseado em cookies HttpOnly, em alternativa ao uso de JWT, e o controlo de acesso é implementado através de um modelo RBAC (Role-Based Access Control) com autoridades granulares.
O frontend comunica com o backend exclusivamente através de um cliente HTTP centralizado, e a gestão de autenticação é encapsulada num contexto React (`AuthContext`).
Este documento engloba as decisões arquiteturais tomadas, o modelo de dados escolhido, os mecanismos de segurança implementados e os resultados obtidos, com referência direta aos ficheiros de código-fonte relevantes.
