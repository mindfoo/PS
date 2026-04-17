# Dicionário de Dados - Sistema de Workflows e RBAC

**Nota:** Todas as tabelas incluem os campos `created_at` e `last_updated` para fins de auditoria.

---

## 🛠 Tabelas de Controlo de Acesso (RBAC)

### 1. Users
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador único do utilizador. |
| **username** | VARCHAR(64) unique not null | Nome de login único. |
| **password_validation** | VARCHAR(256) not null | Hash/Validação da palavra-passe. |
| **display_name** | VARCHAR(64) | Nome de exibição. |
| **role_id** (FK) | UUID not null (id ROLES) | Referência ao papel/perfil do utilizador. |

### 2. Roles
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador único do papel. |
| **name** | (ADMIN, WRITE, READ, DEV) not null | Enumeração do perfil de acesso. |

### 3. RolesFunctions
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador da funcionalidade. |
| **name** | VARCHAR(64) | Nome da função/ação no sistema. |

### 4. RolesPermissions (N:N)
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador da relação. |
| **role_id** (FK) | UUID (id ROLES) | Chave estrangeira para a tabela Roles. |
| **role_functions_id** (FK) | UUID (id ROLESFUNCTIONS) | Chave estrangeira para a tabela RolesFunctions. |

---

## ⚙️ Tabelas de Workflow e Tarefas

### 5. Workflows
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador do fluxo de trabalho. |
| **name** | VARCHAR(64) | Nome do workflow. |
| **created_by** (FK) | UUID (id USERS) | Utilizador que criou o workflow. |

### 6. Tasks
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador da tarefa. |
| **name** | VARCHAR(64) | Nome da tarefa. |
| **config** | JSONB not null | Configurações técnicas da tarefa em formato JSON. |

### 7. WorkflowTasksOrder (N:N)
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | ID único do registo de ordenação. |
| **workflow_id** (FK) | UUID | Referência ao Workflow. |
| **task_id** (FK) | UUID | Referência à Task. |
| **order** | INT not null | Ordem de execução no fluxo. |
| **depends_on_task_id** | UUID not null | ID da tarefa da qual esta depende. |
| **retry_policy** | INT not null | Definição da política de repetições. |

---

## 🚀 Execuções e Alertas

### 8. Executions
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador da execução. |
| **triggered_type** | (MANUAL, CHRON, EVENT) not null | Origem do disparo. |
| **type** | (TASK, WORKFLOW) not null | Se é uma execução de task isolada ou workflow. |
| **status** | (PENDING, RUNNING, SUCCESS, ERROR) | Estado da execução. |
| **output** | JSONB opcional | Dados de saída da execução. |
| **started_at** | TIMESTAMP not null | Data/hora de início. |
| **finished_at** | TIMESTAMP opcional | Data/hora de conclusão. |
| **retry_count** | INT not null | Número de tentativas realizadas. |
| **triggered_by** (FK) | UUID not null (id USERS) | Utilizador que iniciou a execução. |
| **workflow_id** (FK) | UUID nullable (id WORKFLOWS) | Workflow associado. |
| **task_id** (FK) | UUID nullable (id TASKS) | Task associada. |

### 9. Alert
| Atributo | Tipo / Restrição | Descrição |
| :--- | :--- | :--- |
| **id** (PK) | UUID DEFAULT not null | Identificador do alerta. |
| **type** | (WORKFLOW, TASK) | Contexto do alerta. |
| **event** | (EMAIL?, WHATEVER) | Canal ou tipo de evento de alerta. |
| **execution_id** (FK) | UUID nullable (id EXECUTIONS) | Referência à execução que gerou o alerta. |
