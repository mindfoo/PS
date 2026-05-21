# 2. ABSTRACT

This report describes the development of a workflow automation platform, designed as part of a Software Systems course project. The solution was implemented as a full-stack application, comprising a backend service developed in Kotlin 2.3 on top of Spring Boot 3.5, and a frontend interface built with React 18 and TypeScript ~6.0.2.

The adopted architecture follows a three-layer pattern — presentation, business logic, and persistence — with REST-based communication. The domain model encompasses entities such as `Workflow`, `Task`, `Execution`, `Schedule`, and `User`, managed through Spring Data JPA with a PostgreSQL 15 database. Security is ensured by a stateless authentication mechanism based on HttpOnly cookies, as an alternative to JWT, and access control is implemented through an RBAC (Role-Based Access Control) model with granular authorities.

Business logic in the service layer leverages the functional `Either<E, T>` pattern, which explicitly distinguishes successful results from domain failures without relying on exceptions. The frontend communicates with the backend exclusively through a centralised HTTP client, and authentication management is encapsulated in a React context (`AuthContext`).

The report documents the architectural decisions made, the detailed domain model, the implemented security mechanisms, and the results obtained, with direct reference to relevant source code files.

**Keywords:** workflow automation, Spring Boot, Kotlin, React, TypeScript, RBAC, Either, JPA, PostgreSQL.
