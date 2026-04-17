```
Licenciatura	em	Engenharia	Informática	
e	de	Computadores
Projeto	e	Seminário
Semestre	de	Verão	202 5 /202 6	
```
## Proposta	de	Projeto:

# Work%low Platform	– Plataforma	para

# orquestração	e	monitorização	de	processos

# aplicacionais

### 50929	 – Ana	Margarida	Pascoal,	e-mail:	A50929@alunos.isel.pt

### Orientador:

### Pedro	Miguens,	e-mail:	pedro.miguens@isel.pt

### 7	 de	Março de	202 6

## 1 Introdução

```
Pretende-se	 o	 desenvolvimento	 de	 uma	 infraestrutura	 integrada	 para	 controlo,	
orquestração	e	monitorização	de	processos	aplicacionais,	concebida	para	suportar	a	execução	
programada	e	supervisionada	de	processos.	A	solução	deverá	assegurar	elevados	nı́veis	de
Liabilidade,	 rastreabilidade,	 eLiciência	 operacional	 e	 segurança,	 permitindo	 uma	 gestão	
centralizada	e	automatizada	do	ciclo	de	vida	dos	processos. Em	suma,	uma	aplicação	para	criar	
e	gerir	 pipeline / work*low de	processos.
```
## 2 Descrição	do	Sistema

```
A	infraestrutura	a	implementar	deverá	disponibilizar	um	motor	de	execução	capaz	de	gerir	
processos,	 subprocessos	 e	 tarefas	 associadas,	 suportando	 diferentes	 modos	 de	 disparo,	
nomeadamente	execução	programada,	baseada	em	eventos	ou	por	via	manual	de utilizadores	
autorizados.	O	sistema	deverá	permitir	a	deLinição	de	dependências	entre	tarefas,	execução	
sequencial	 ou	 paralela	 e	 mecanismos	 de	 reexecução	 controlada,	 garantindo	 consistência	 e	
controlo	transacional	ao	longo	de	todo	o	 work*low.
```
## 3 Requisitos

```
Foram	deLinidos	os	seguintes	requisitos	para	a	realização	do	projeto:
```
- Base	 de	 dados	 para	 armazenar	 informção	 sobre	 utilizadores,	 os	 seus	 acessos, os
  _work*lows_ gerados pelos	mesmos e	os	seus	respectivos	 _logs_ de	execução;

```
§ Utilizadores:	 contas	 de	 utilizador	 com	 os	 seus	 dados	 e	 nı́veis	 de	 permissão	
cumulativas:
```

```
o “Admin”:	 com	 permissõ es	 má ximas,	 controla	 acessos	 à aplicaçã o,	 roles de	
outros	utilizadores	e	as	suas	permissões	de	acesso;
o “Writer”:	 tem	 acesso	 a	 criar	 novos	 work*lows customizáveis	 às	 suas	
necessidades,	pode	fazer	 upload de	processos/tarefas	e	herda	permissões	
de	leitura	do	perLil	de	“Reader”;
o “Reader”:	apenas	acede	a	 logs e	não	tem	permissões	de	escrita;
§ Pipelines / work*lows :	associadas	a	cada	utilizador;
§ Tarefas/processos:	associadas	a	cada	 work*low e	deLinidas	previamente;
§ Histórico	de	execuções.
```
- O	 sistema	 a	 desenvolver	 deverá	 manter	 o	 registo	 detalhado	 da	 execução,	 incluindo
  estados,	 tempos	 de	 inı́cio	 e	 Lim,	 duração,	 resultados	 e	 mensagens	 de	 erro.	 Esta
  informação	permitirá	assegurar	total	rastreabilidade	e	suportar	capacidades	avançadas
  de	monitorização;
- Em	 termos	 de	 robustez	 operacional,	 a	 plataforma	 deverá	 incorporar	 mecanismos
  automáticos	de	deteção	e	tratamento	de	erros,	com	polı́ticas	conLiguráveis	por	processo	ou
  tarefa.	Em	caso	de	falha,	o	sistema	deverá	ser	capaz	de	executar	novas	tentativas	de	forma
  automática,	gerar	notiLicações	de	alarme	através	de	canais	conLiguráveis	e	tomar	decisões
  automáticas,	incluindo	o	abortar	da	execução	de	tarefas	subsequentes	sempre	que	deLinido
  pelas	regras	do	 _wor*low_ ;
- A	segurança	será	assegurada	através	da	implementação	de	um	modelo	de	controlo	de
  acessos	 baseado	 em	 papéis	 (RBAC),	 permitindo	 a	 distinção	 clara	 de	 privilégios	 entre
  diferentes	perLis	de	utilizador;
- Para	suportar	a	conLiguração,	operação	e	supervisão	da	infraestrutura,	será	desenvolvida
  uma	aplicação	web	que	funcionará	como	interface	uniLicada	do	sistema.	Esta	interface
  deverá	permitir	a	monitorização	em	tempo	real	das	execuções,	consulta	de	histórico,
  conLiguração	 de	 processos	 e	 agendamentos,	 gestão	 de	 alertas	 e	 administração	 de
  utilizadores	e	perLis;
- Aplicação que	permita	gerar	 _pipelines_ / _work*lows_ conLiguráveis.

Caso	sejam	atingidos	todos	os	requisitos	obrigatórios,	poder-se-á considerar	os	seguintes
requisitos	opcionais:

- Monitorização	em	tempo	real	recorrendo	a	SSEs	ou	Web	sockets;
- Alarmı́stica	para	email	ou	 _log_ externo.


## 4 Arquitetura	do	Sistema

```
Na	 Figura	 1	 está	 apresentada	 a	 visão	 geral	 do	 sistema	 que	 será	 desenvolvido	 e	
implementado.	Neste	diagrama,	um	cliente	acede	à	aplicação	 Web,	 esta comunica	com	uma	 API	
num	servidor.	Esta	 API	 comunica	com	a	base	de	dados	para	armazenar	de	forma	persistente	a	
informação	relevante	ao	funcionamento	da	infraestrutura.	Nomeadamente,	a	informação	dos	
utilizadores,	os	diferentes	 work*lows criados,	as	tarefas/processos	deLinidos	pelo	utilizador	e	
respetivos	 logs de	execução	de	cada	um	deles.

Project Architecture Diagram
The diagram illustrates a software architecture system, divided into two main components: a Client side and a Server side.

Client Side:

User: Interacts with the Frontend - Dashboard.

Frontend - Dashboard: Communicates bidirectionally with the Server's API / Backend.

Server Side:

API / Backend: Communicates bidirectionally with the Scheduler.

Scheduler: Interacts bidirectionally with the API / Backend. It has a unidirectional flow pointing to the Executor and another unidirectional flow pointing to the Database (BD).

Executor: Has a unidirectional data flow pointing towards the Database (BD).

Database (BD): A database cylinder icon where both Scheduler and Executor unidirectional flows end, indicating data is written to or processed for the database by these components.

Flow Summary: The system shows a top-down and left-to-right flow. Users engage with the Frontend (Client), which interacts with the API/Backend (Server). The API communicates with a Scheduler, which can then trigger an Executor. Both Scheduler and Executor write or interact with the Database (BD).
```
```
Figura	1:	Sistema	de	arquitectura	do	projeto
```
### 4.1 Base	de	Dados

```
A	base	de	dados	a implementar neste	sistema	deverá ser	em	Postgres[1].
```
### 4.2 API

```
O	 back-end	 deste	sistema	será	em	Kotlin[ 2 ] e	implementará uma	 API	 que	expõe	 endpoints	
que	possibilitam	o	acesso	ao	conteúdo	da	base	de	dados.
```
### 4.3 Aplicação Web

```
A	aplicação Web	 que	será desenvolvida	em	ReactJS[3] permitirá ao	utilizador	realizar as	
acções	de	CRUD	associadas	a	uma	 pipeline / work*low ,	às	tarefas/processos e	dependências	das
mesmas,	assim	como	o sistema	de	alarmı́stica para	cada	pipeline.
```
## 5 Riscos

```
Na	realizaçãao	do	projeto	foram	identiLicados	os	seguintes	riscos:
```
- A	possı́vel	diLiculdade de	implementação	dos	requisitios	opcionais;
- A	implementação	da	execução	condicional	de	 _pipelines_ e	a	reexecução	de	determinadas
  tarefas	que	possam	falhar..


## 6 Planeamento

```
Em	seguida,	apresenta-se	o	 Gantt	Chart	 na	Figura	2	que	contém	o	planeamento	do	projeto.
```
```
Figura	2:	Gantt	Chart
```
## 7 Referências	BibliográEicas

```
[1] PostgreSQL	Global	Development	Group,	"PostgreSQL:	The	World's	Most	Advanced	Open	
Source	Relational	Database."	[Online].	Disponı́vel	em: https://www.postgresql.org/.	[Acedido	
a:	7-Mar-2026].
```
```
[2] JetBrains,	"Kotlin	Programming	Language."	[Online].	Disponı́vel	em:
https://kotlinlang.org/.	[Acedido	a:	7-Mar-2026].
```
```
[3] Meta	Open	Source,	"React."	[Online].	Disponı́vel	em: https://react.dev/.	[Acedido	a:	7-Mar-
2026].
```

