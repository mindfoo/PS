# 2. ABSTRACT

This report describes the development of a process automation platform ("workflows"), conceived within the scope of the Project and Seminar curricular unit. The solution was implemented as a full-stack application, composed of a backend service developed in Kotlin 2.3 on Spring Boot 3.5, and a frontend interface built with React 18 and TypeScript 6.0.2.
The adopted architecture follows a three-layer pattern: presentation, business logic, and persistence — with communication based on REST. The domain model encompasses entities such as: Workflow, Task, Execution, Schedule, and User, managed through Spring Data JPA with a PostgreSQL 15 database.
API security is ensured by a stateless authentication mechanism based on HttpOnly cookies, as an alternative to using JWT, and access control is implemented through an RBAC (Role-Based Access Control) model with granular authorities.
The frontend communicates with the backend exclusively through a centralized HTTP client, and authentication management is encapsulated in a React context (AuthContext).
This document encompasses the architectural decisions made, the chosen data model, the security mechanisms implemented, and the results obtained, with direct reference to the relevant source-code files.
