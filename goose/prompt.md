# FlowCore Technical Specification

## Executive summary

FlowCore is an open-source Java platform for building **stateful** business workflows whose execution must remain **transactionally safe**, **auditable**, **observable**, and resilient under retries and partial failures. The core abstraction is a **workflow instance** that progresses via a **validated state machine**; execution is driven by commands/signals and produces events, with optional asynchronous steps executed via Kafka-backed workers and provider adapters. The demos (Virtual Card Issuance and Payment Lifecycle) are intentionally fintech-flavored to showcase complex business logic (strict state transitions, retries, compensations, idempotency) while remaining generic and reusable as a platform foundation.

This specification is designed to be directly consumable by a code-generation LLM: it defines repository structure, module APIs, schemas, DSL grammar, execution semantics, reliability guarantees, observability instrumentation, security controls, test suites, CI/CD gates, docker-compose deployment, and acceptance criteria with numeric targets.

Primary design anchors:
- **Modularity and boundaries:** Spring Modulith module concepts (provided/required interfaces, structural validation, module documentation/testing). citeturn0search4turn0search0  
- **Observability-first:** Spring Boot observability uses Micrometer Observation; OTEL-compatible exports via OTLP. citeturn2search0turn2search4turn7search3  
- **Messaging semantics:** Kafka transactions / read-committed to support EOS *within Kafka*; other sinks (e.g., Postgres) require idempotency/dedup cooperation. citeturn0search25turn1search3  
- **Database correctness:** Postgres transaction isolation semantics, explicit locking, and retry on serialization failures when Serializable is used. citeturn1search0turn1search16  
- **Outbox/inbox patterns:** Transactional outbox + idempotent consumer to handle at-least-once delivery safely. citeturn1search2turn1search6turn1search1  
- **Security baselines:** JWT (RFC 7519), HTTP message signatures for webhooks (RFC 9421), ABAC reference (NIST SP 800-162), and secure logging guidance (OWASP). citeturn2search2turn2search3turn3search0turn3search5turn3search9  

Named technology stack references used by this spec:
entity["organization","Spring Boot","java framework"], entity["organization","Spring Modulith","spring modular monolith"], entity["organization","Apache Kafka","distributed event streaming"], entity["organization","PostgreSQL","relational database"], entity["organization","OpenTelemetry","observability standard"], entity["organization","Prometheus","metrics monitoring"], entity["organization","Grafana","observability dashboards"], entity["organization","Grafana Loki","log aggregation"], entity["organization","Grafana Tempo","distributed tracing backend"], entity["organization","Micrometer","metrics and observation"], entity["company","Docker","container platform"], entity["company","GitHub","code hosting"], entity["organization","GitHub Actions","ci platform"], entity["organization","Testcontainers","test infrastructure"], entity["organization","WireMock","http mocking"], entity["organization","OWASP","application security"], entity["organization","NIST","standards institute"], entity["organization","IETF","internet standards body"], entity["organization","Resilience4j","fault tolerance library"], entity["company","Telegram","messaging platform"].

## Scope, goals, and non-goals

**Scope**
- Provide a reusable platform/library set for defining and executing workflows as persisted state machines with:
  - deterministic transitions and guards,
  - durable execution state,
  - asynchronous step execution support,
  - retries/backoff/timeouts,
  - compensation (Saga-style),
  - timers/scheduled transitions,
  - parallel branches (fork/join),
  - durable outbox/inbox/idempotency.
- Provide two demo applications:
  - Virtual card issuance lifecycle workflow.
  - Payment lifecycle workflow.
- Provide an observability stack (Prometheus + Grafana + Loki + Tempo + OTEL Collector) for one-command local run. citeturn5search2turn4search2turn6search2turn6search1  

**Goals**
- **Correctness under failure:** must tolerate crashes and restarts without corrupting workflow state; must be safe under message redelivery (idempotent consumer). citeturn1search6turn1search2  
- **Clear transactional boundaries:** workflow state updates are ACID in Postgres; event publication uses outbox or Kafka EOS where applicable. citeturn1search0turn1search1turn1search3turn0search25  
- **Strict modularity:** demo apps must demonstrate a modular monolith structure with explicit module boundaries, module verification tests, and module documentation artifacts. citeturn0search4turn0search8  
- **Production-grade observability:** traces/metrics/logs for every command, transition, step, outbox publish, adapter call; dashboards included; correlation IDs consistent. citeturn2search0turn0search2turn0search3turn6search2  
- **Security mindset:** authn/authz hooks, audit log, masking rules, signed webhooks, least-privilege patterns. citeturn2search2turn2search3turn3search0turn3search5turn3search1  

**Non-goals**
- Not a full BPMN engine (no BPMN editor, no graphical designer, no human task inbox UI).
- Not a distributed transaction coordinator (no XA / 2PC across Postgres and Kafka by default); cross-system exactly-once is out of scope—handled via outbox + idempotency or CDC-based relays. Kafka itself notes that EOS to other destination systems requires cooperation with those systems. citeturn0search25turn1search2  
- Not a full IAM product (no user directory, no OAuth server); it validates JWTs and applies RBAC/ABAC policies.
- Not a full payment processor; demos simulate providers with mock adapters.

**Unspecified details explicitly marked as “no specific constraint”**
- Exact service port numbers (defaults provided; configurable).
- Choice of Kafka Docker image/distribution (must support transactions; broker version must satisfy Spring Kafka EOS requirements). citeturn1search3  
- Choice of JSON logging encoder library (must emit required fields).
- Docker resource limits (defaults provided; adjustable).

## Architecture and modules

**High-level architecture (runtime view)**

```mermaid
flowchart LR
  subgraph Client
    A[HTTP Client / Demo UI]
    T[Telegram WebApp Client]
  end

  subgraph DemoApp["Demo Spring Boot App"]
    API[REST API + OpenAPI]
    AUTH[JWT AuthN + RBAC/ABAC AuthZ]
    ENG[Workflow Engine]
    REG[Workflow Registry]
    ST[State Machine Validator]
    TX[Tx Layer: Idempotency + Inbox + Outbox]
    DB[(PostgreSQL)]
    PUB[Outbox Publisher]
    KAF[Kafka Adapter]
    WORK[Worker: Provider Calls]
    INT[Provider Adapters (HTTP/Webhook)]
    OBS[Observability (Metrics/Traces/Logs)]
  end

  subgraph Kafka["Kafka Cluster"]
    K1[(Topics: commands/events)]
  end

  subgraph ObsStack["Observability Stack"]
    PR[(Prometheus)]
    GR[(Grafana)]
    LK[(Loki)]
    TP[(Tempo)]
    OT[(OTel Collector)]
  end

  A --> API
  T --> API
  API --> AUTH --> ENG
  ENG --> REG
  ENG --> ST
  ENG --> TX --> DB
  TX --> PUB --> KAF --> K1
  K1 --> WORK --> INT
  WORK --> KAF --> K1
  ENG --> OBS
  WORK --> OBS

  OBS --> OT
  OT --> TP
  OT --> LK
  API --> PR
  GR --> PR
  GR --> LK
  GR --> TP
```

This diagram reflects required properties:
- Modular structure: engine, transactional layer, adapters, observability, and security are separable components; demo apps compose them.
- Outbox decouples DB writes from message publication. citeturn1search2turn1search1  
- Kafka EOS is supported for read→process→write sequences within Kafka when enabled/configured; DB correctness still depends on idempotent writes/inbox dedup. citeturn1search3turn0search25turn1search6  
- Observability uses Spring Boot’s observation facilities and OTEL semantic conventions. citeturn2search0turn0search2turn0search6turn0search22  

**Repository structure (deliverable)**

```text
flowcore/
  pom.xml                         # parent aggregator + dependency mgmt
  LICENSE                         # Apache-2.0 recommended
  README.md
  CONTRIBUTING.md
  SECURITY.md
  CODE_OF_CONDUCT.md

  docs/
    architecture.md
    dsl-spec.md
    runbook.md
    threat-model.md
    adr/
    openapi/
      demo-card-issuance.yaml
      demo-payment-lifecycle.yaml

  build/
    checkstyle.xml
    spotbugs-exclude.xml

  deploy/
    compose/
      docker-compose.yml
      .env.example
      prometheus/
        prometheus.yml
      grafana/
        provisioning/
          datasources/
            datasources.yml
          dashboards/
            dashboards.yml
        dashboards/
          flowcore-overview.json
          flowcore-workflows.json
          flowcore-kafka.json
      loki/
        loki.yml
      tempo/
        tempo.yml
      otel-collector/
        otel-collector.yml

  migrations/
    V1__flowcore_core.sql
    V2__flowcore_outbox_inbox.sql
    V3__flowcore_audit.sql
    V4__demo_seed_data.sql

  flowcore-bom/                   # optional: dependency BOM
  flowcore-api/                   # public API (interfaces + DTOs)
  flowcore-runtime/
  flowcore-statemachine/
  flowcore-transaction/
  flowcore-kafka/
  flowcore-observability/
  flowcore-security/
  flowcore-integrations/
  flowcore-starter/
  demo-card-issuance/
  demo-payment-lifecycle/
```

**Build system**
- Maven multi-module project; parent POM manages dependency versions; demos are runnable Spring Boot apps.
- Java 21 toolchain. (This is a requirement, not a researched fact.)

**Module list with responsibilities, packages, and public APIs (normative)**

The platform uses the base groupId `io.flowcore` and Java packages rooted at `io.flowcore.*`.

| Maven module | ArtifactId | Base package | Primary responsibility | Public API surface |
|---|---|---|---|---|
| flowcore-api | flowcore-api | `io.flowcore.api` | Stable platform interfaces + DTOs | Pure interfaces/DTOs only |
| flowcore-runtime | flowcore-runtime | `io.flowcore.runtime` | Engine: execute workflows, persist state, dispatch steps | `WorkflowEngine`, `WorkflowRegistry`, `WorkflowContext`, `WorkflowQueryApi` |
| flowcore-statemachine | flowcore-statemachine | `io.flowcore.statemachine` | DSL model, validation, compilation to executable graph | `WorkflowDefinition`, `WorkflowValidator`, `WorkflowCompiler` |
| flowcore-transaction | flowcore-transaction | `io.flowcore.tx` | Idempotency, inbox/outbox, saga log, timers | `IdempotencyService`, `OutboxService`, `InboxService` |
| flowcore-kafka | flowcore-kafka | `io.flowcore.kafka` | Kafka producers/consumers, EOS configuration, serializers | `KafkaEventPublisher`, `KafkaCommandConsumer` |
| flowcore-observability | flowcore-observability | `io.flowcore.obs` | Metrics/tracing conventions, logging correlation, dashboard artifacts | `FlowcoreObservationConvention`, `FlowcoreMetrics` |
| flowcore-security | flowcore-security | `io.flowcore.security` | JWT authn, RBAC/ABAC, webhook verification, audit log | `AuthorizationService`, `PolicyEvaluator`, `WebhookVerifier` |
| flowcore-integrations | flowcore-integrations | `io.flowcore.integrations` | Provider adapter contracts + HTTP client wrappers | `ProviderAdapter`, `WebhookHandler` |
| flowcore-starter | flowcore-starter | `io.flowcore.starter` | Spring Boot auto-config starter (imports all modules) | `@AutoConfiguration` + `FlowcoreProperties` |
| demo-card-issuance | demo-card-issuance | `io.flowcore.demo.card` | Demo app: card issuance workflow + APIs | REST controllers and workflows |
| demo-payment-lifecycle | demo-payment-lifecycle | `io.flowcore.demo.payment` | Demo app: payment workflow + APIs | REST controllers and workflows |

**Spring Modulith usage requirement (for demos)**
- Each demo application must be structured into logical modules using package-level boundaries and Modulith verification tests (module “provided/required interface” concepts). citeturn0search4turn0search32turn0search16  
- Each demo must generate Modulith documentation snippets (module component diagrams) during CI using Spring Modulith’s `Documenter` approach. citeturn0search8  

**Database schema (Postgres) for core platform**

FlowCore requires a single Postgres schema (default `flowcore`) with JSONB for context/payloads and strict uniqueness constraints for idempotency and dedup. Postgres isolation semantics and locking behavior are relied upon for concurrency safety. citeturn1search0turn1search16  

SQL migrations must be delivered as Flyway-style scripts (`migrations/V*__*.sql`). (Flyway choice is normative; could be Liquibase if swapped.)

Core tables (normative DDL; minimal but complete for code generation):

```sql
-- migrations/V1__flowcore_core.sql
CREATE SCHEMA IF NOT EXISTS flowcore;

CREATE TABLE flowcore.workflow_instance (
  id                UUID PRIMARY KEY,
  workflow_type     TEXT NOT NULL,
  business_key      TEXT NOT NULL,
  status            TEXT NOT NULL,  -- RUNNING | COMPLETED | FAILED | CANCELED
  current_state     TEXT NOT NULL,
  version           BIGINT NOT NULL,
  context_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_workflow_instance_type_key
  ON flowcore.workflow_instance (workflow_type, business_key);

CREATE INDEX ix_workflow_instance_status
  ON flowcore.workflow_instance (status);

CREATE TABLE flowcore.workflow_token (
  id                UUID PRIMARY KEY,
  workflow_instance_id UUID NOT NULL REFERENCES flowcore.workflow_instance(id) ON DELETE CASCADE,
  token_name        TEXT NOT NULL,      -- e.g. "main", "branch-1"
  active_node       TEXT NOT NULL,      -- step or state node id
  status            TEXT NOT NULL,      -- ACTIVE | WAITING | JOINED | CLOSED
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_token_unique
  ON flowcore.workflow_token(workflow_instance_id, token_name);

CREATE TABLE flowcore.workflow_step_execution (
  id                  UUID PRIMARY KEY,
  workflow_instance_id UUID NOT NULL REFERENCES flowcore.workflow_instance(id) ON DELETE CASCADE,
  token_id             UUID NOT NULL REFERENCES flowcore.workflow_token(id) ON DELETE CASCADE,
  step_id              TEXT NOT NULL,
  attempt              INT NOT NULL,
  status               TEXT NOT NULL, -- STARTED | SUCCEEDED | FAILED | COMPENSATED | SKIPPED
  started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at          TIMESTAMPTZ,
  error_code           TEXT,
  error_detail         TEXT,
  result_json          JSONB
);

CREATE INDEX ix_step_exec_instance_step
  ON flowcore.workflow_step_execution(workflow_instance_id, step_id);

CREATE TABLE flowcore.workflow_timer (
  id                  UUID PRIMARY KEY,
  workflow_instance_id UUID NOT NULL REFERENCES flowcore.workflow_instance(id) ON DELETE CASCADE,
  token_id             UUID NOT NULL REFERENCES flowcore.workflow_token(id) ON DELETE CASCADE,
  timer_name           TEXT NOT NULL,     -- e.g. "KYC_TIMEOUT"
  due_at               TIMESTAMPTZ NOT NULL,
  payload_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
  status               TEXT NOT NULL,     -- SCHEDULED | FIRED | CANCELED
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_timer_due_status
  ON flowcore.workflow_timer(status, due_at);
```

```sql
-- migrations/V2__flowcore_outbox_inbox.sql
CREATE TABLE flowcore.outbox_event (
  id                UUID PRIMARY KEY,
  aggregate_type    TEXT NOT NULL,         -- workflow_type or domain aggregate
  aggregate_id      TEXT NOT NULL,         -- business_key
  event_type        TEXT NOT NULL,
  event_key         TEXT NOT NULL,         -- deterministic key for dedup downstream
  payload_json      JSONB NOT NULL,
  headers_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
  status            TEXT NOT NULL,         -- PENDING | PUBLISHED | FAILED | DEAD
  publish_attempts  INT NOT NULL DEFAULT 0,
  next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_outbox_event_key
  ON flowcore.outbox_event(event_key);

CREATE INDEX ix_outbox_pending
  ON flowcore.outbox_event(status, next_attempt_at);

CREATE TABLE flowcore.inbox_message (
  id                UUID PRIMARY KEY,
  source            TEXT NOT NULL,         -- kafka|webhook|http
  message_id        TEXT NOT NULL,         -- upstream id (event_key, kafka key+offset, webhook signature input id)
  consumer_group    TEXT NOT NULL,
  received_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_inbox_dedup
  ON flowcore.inbox_message(source, message_id, consumer_group);

CREATE TABLE flowcore.idempotency_key (
  id                UUID PRIMARY KEY,
  scope             TEXT NOT NULL,         -- "http:POST:/cards/issue", "command:StartWorkflow"
  key               TEXT NOT NULL,
  request_hash      TEXT NOT NULL,
  response_json     JSONB,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at        TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_idempotency_scope_key
  ON flowcore.idempotency_key(scope, key);
```

```sql
-- migrations/V3__flowcore_audit.sql
CREATE TABLE flowcore.audit_log (
  id                UUID PRIMARY KEY,
  actor_sub         TEXT,                  -- JWT subject
  actor_roles       TEXT[],
  actor_tenant      TEXT,
  action            TEXT NOT NULL,         -- e.g. "workflow.start", "payment.capture"
  object_type       TEXT NOT NULL,         -- e.g. "workflow_instance"
  object_id         TEXT NOT NULL,
  decision          TEXT NOT NULL,         -- ALLOW | DENY
  reason            TEXT,
  trace_id          TEXT,
  span_id           TEXT,
  request_ip        INET,
  user_agent        TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  details_json      JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_audit_object
  ON flowcore.audit_log(object_type, object_id);

CREATE INDEX ix_audit_created
  ON flowcore.audit_log(created_at);
```

Schema decisions are intentionally aligned with idempotent consumer/outbox guidance (dedup keys, at-least-once publication, consumer idempotency). citeturn1search2turn1search6turn1search1  

## Workflow DSL and engine semantics

FlowCore supports defining workflows via:
- **Java DSL** (compile-time refactor safety).
- **YAML DSL** (ops-friendly, dynamic load, easy to demo).

**Core conceptual model (normative)**
- A **workflow definition** contains:
  - `workflowType` (string)
  - `version` (int)
  - `states` (set)
  - `initialState`
  - `terminalStates`
  - `steps` (nodes) linked to transitions
  - `transitions` with guards and triggers
  - `retryPolicy` and `timeoutPolicy` per step/transition
  - `compensations` per step
  - `fork/join` nodes for parallelism
  - `timers` and scheduled triggers

**Execution semantics overview (normative)**
- The engine processes **commands** (start/signal/complete step/timeout fired) inside a DB transaction:
  1. Load workflow instance (or create on start).
  2. Verify command idempotency (optional but recommended for HTTP/API commands).
  3. Validate permissible transition(s) from current state/node(s).
  4. For each runnable step:
     - create a `workflow_step_execution` row with `attempt=1, status=STARTED`
     - if step is synchronous, execute handler and mark success/fail
     - if asynchronous, enqueue work item (outbox event or internal queue) and mark token WAITING
  5. Persist new state/token positions, schedule timers, append outbox events.
  6. Commit transaction.

This is consistent with platform patterns:
- transactions define deterministic internal state updates, while asynchronous boundaries rely on outbox/idempotency. citeturn1search0turn1search2turn1search6  

**Validation rules (normative)**
At workflow definition load/boot time, reject the definition if any rule fails:
- `workflowType` must match `^[a-zA-Z0-9_.-]{3,64}$`.
- `initialState` must be in `states`.
- All transitions’ `from` and `to` states exist.
- No unreachable states (graph reachability from initial state).
- No dead-end non-terminal state (unless explicitly allowed with `allowDeadEnds=true`).
- Step IDs are unique and referenced by transitions.
- Guard expressions must compile (Java: lambda compile; YAML: expression parse).
- Retry policy:
  - `maxAttempts >= 1`
  - `backoff` must be either fixed or exponential with `baseDelayMs >= 10` and `maxDelayMs >= baseDelayMs`.
- Timeout:
  - `timeoutMs >= 100`
  - if timeout exists, a `onTimeout` transition must exist for that step.
- Fork/join:
  - fork must declare branch names; join must reference all branch names.
  - join must define behavior on partial completion (default: wait for all).
- Timer names unique per workflow; dueAt must be computed deterministically from context or explicit timestamp.

**Guard language (YAML)**
- Use Spring Expression Language (SpEL) subset or a minimal boolean expression language.
- For code generation simplicity: specify a minimal expression grammar:

EBNF (normative):
```text
guard        := orExpr ;
orExpr       := andExpr ( "||" andExpr )* ;
andExpr      := eqExpr ( "&&" eqExpr )* ;
eqExpr       := relExpr ( ("==" | "!=") relExpr )* ;
relExpr      := primary ((">" | ">=" | "<" | "<=") primary)* ;
primary      := literal | variable | functionCall | "(" guard ")" ;
literal      := "true" | "false" | number | string ;
variable     := "$." identifier ( "." identifier )* ;
functionCall := identifier "(" (guard ("," guard)*)? ")" ;
identifier   := [A-Za-z_][A-Za-z0-9_]* ;
```

Semantics:
- variables reference workflow context JSON (`context_json`) via JSONPath-like `$.path.to.value`.
- built-in functions:
  - `exists(x)`, `len(x)`, `contains(a,b)`, `regex(s,pattern)`.

**Java DSL (normative API)**
Package: `io.flowcore.statemachine.dsl`

Key public types (must exist):
- `WorkflowDsl`
- `WorkflowBuilder`
- `StateBuilder`
- `StepBuilder`
- `RetryPolicy`
- `TimeoutPolicy`
- `CompensationSpec`
- `ForkSpec`, `JoinSpec`

Example:

```java
WorkflowDefinition def = WorkflowDsl
  .workflow("demo.card.issuance", 1)
  .states("INIT","KYC_PENDING","KYC_APPROVED","CARD_PROVISIONING","WALLET_BINDING","ACTIVE","FAILED")
  .initial("INIT")
  .terminal("ACTIVE","FAILED")
  .step("kycCheck")
    .fromState("INIT").toState("KYC_PENDING")
    .asyncActivity("KycProviderAdapter", "verify")
    .retry(RetryPolicy.exponential(5, 200, 5000))
    .timeout(TimeoutPolicy.afterMs(10_000).onTimeoutTransition("FAILED"))
    .compensateWith("kycCompensate")
  .step("provisionCard")
    .fromState("KYC_APPROVED").toState("CARD_PROVISIONING")
    .asyncActivity("IssuerAdapter", "provisionVirtualCard")
  .transition("KYC_PENDING", "KYC_APPROVED")
    .onEvent("KYC_RESULT").guard("$.kyc.status == \"APPROVED\"")
  .transition("KYC_PENDING", "FAILED")
    .onEvent("KYC_RESULT").guard("$.kyc.status != \"APPROVED\"")
  .build();
```

**YAML DSL format (normative)**

Minimal YAML schema:

```yaml
workflowType: "demo.card.issuance"
version: 1
states: [INIT, KYC_PENDING, KYC_APPROVED, CARD_PROVISIONING, WALLET_BINDING, ACTIVE, FAILED]
initialState: INIT
terminalStates: [ACTIVE, FAILED]

steps:
  - id: kycCheck
    kind: asyncActivity
    from: INIT
    to: KYC_PENDING
    activity:
      adapter: KycProviderAdapter
      operation: verify
    retry:
      mode: exponential
      maxAttempts: 5
      baseDelayMs: 200
      maxDelayMs: 5000
      jitterPct: 20
    timeout:
      timeoutMs: 10000
      onTimeoutState: FAILED
    compensation:
      kind: asyncActivity
      activity:
        adapter: KycProviderAdapter
        operation: cancelVerification

transitions:
  - id: kycApproved
    from: KYC_PENDING
    to: KYC_APPROVED
    trigger:
      type: event
      name: KYC_RESULT
    guard: '$.kyc.status == "APPROVED"'

  - id: kycDeclined
    from: KYC_PENDING
    to: FAILED
    trigger:
      type: event
      name: KYC_RESULT
    guard: '$.kyc.status != "APPROVED"'
```

**Scheduler/timers (normative semantics)**
- Timeouts and scheduled tasks must be persisted in `workflow_timer`.
- A scheduler component runs every `flowcore.timer.pollIntervalMs` (default 500ms) and:
  - selects due timers with `status=SCHEDULED` and `due_at <= now()` using row locking (prefer `FOR UPDATE SKIP LOCKED` for concurrency),
  - emits a `TIMER_FIRED` internal signal to the engine,
  - marks timers FIRED within the same tx.

This relies on Postgres concurrency/locking correctness. citeturn1search16turn1search0  

**Parallel branches (fork/join) (normative)**
- A fork creates N tokens (`workflow_token`) with distinct `token_name`.
- Each branch advances independently.
- A join waits until all declared branch tokens reach the join node (or terminal) then consolidates to a single token:
  - join policy: `ALL` (default) or `ANY` (optional).
- If a branch fails and policy is ALL, engine triggers compensations for completed branches (configurable).

## Reliability, transactions, and messaging

This section defines transactional guarantees, outbox/inbox/idempotency, saga semantics, Kafka EOS usage, and recovery flows.

**Postgres transaction model (normative)**
- Default isolation level: `READ COMMITTED`.
- For invariants that span multiple rows (e.g., “only one active workflow instance per (type,business_key)”): enforce via unique constraints and handle constraint violations, or optionally run those operations at `SERIALIZABLE` and implement retry-on-serialization-failure. Postgres defines Serializable as equivalent to some serial order (subject to retry on anomalies). citeturn1search0  

**Idempotency keys (HTTP + commands)**
- For all *externally initiated* commands (HTTP POST/PUT and incoming webhooks), require an idempotency key header:
  - `Idempotency-Key: <string>` for HTTP
  - `X-Flowcore-Command-Id: <uuid>` for internal commands
- Store normalized scope (method+path) + key + request hash in `flowcore.idempotency_key`.
- Behavior:
  - If key not found → execute command, store response JSON.
  - If found and request hash matches → return stored response (exact replay).
  - If found but request hash differs → return `409 Conflict` with error code `IDEMPOTENCY_KEY_REUSE_MISMATCH`.

This aligns with the general need for idempotent handling under retries and duplicates in distributed systems. citeturn1search6  

**Inbox deduplication (idempotent consumer pattern)**
- Every Kafka consumer and webhook handler must call `InboxService.tryAccept(source, messageId, consumerGroup)`:
  - Insert row into `inbox_message`.
  - If unique constraint violation → treat as duplicate; do not re-apply side effects.
- This matches idempotent consumer guidance: consumers must tolerate receiving the same message more than once. citeturn1search6turn1search2  

**Transactional outbox**
- Outbox is written in the same DB transaction as workflow state updates.
- Message relay runs separately (in-process polling by default; optional CDC relay).
- Outbox guarantees:
  - Workflow state and the intent to publish an event are atomically committed in Postgres.
  - Event publication is **at-least-once** (duplicates possible); downstream consumers must be idempotent. citeturn1search2turn1search1  

Outbox publication options (normative requirements):
- **Polling publisher (required):**
  - Periodically fetch PENDING events due for publish.
  - Publish to Kafka.
  - Mark PUBLISHED only after successful publish ack.
  - Use backoff policy to update `next_attempt_at` on failure.
  - Use `FOR UPDATE SKIP LOCKED` to allow N publishers in parallel without double-processing.
  - This corresponds to the “polling publisher” approach in outbox ecosystems. citeturn1search26turn5search2  
- **CDC publisher (optional “bonus”):**
  - Provide a docker-compose profile to run Debezium and use its outbox event router.
  - This is optional because it adds operational complexity, but it is a known approach in Debezium docs. citeturn1search1  

**Kafka exactly-once semantics (EOS) in FlowCore**
- Kafka can provide EOS for read→process→write to Kafka *when using transactions and read_committed consumers*; Kafka documentation explicitly describes the producer’s ability to atomically update committed offsets as part of the transaction, enabling EOS-style processing. citeturn0search25turn1search3  
- FlowCore must implement two modes:
  - `EOS_DISABLED` (default): normal at-least-once consumers with inbox dedup.
  - `EOS_ENABLED`: use Spring Kafka EOS mode with transactional producers + consumer isolation = read_committed. Spring Kafka documents EOS behavior and requirements. citeturn1search3turn0search25  

**Important limitation (must be documented in README)**
- EOS guarantees apply to Kafka topics and consumer offsets; once Postgres changes are involved, “exactly-once end-to-end” requires cooperation (idempotent writes, inbox dedup, constraints). Kafka’s own design notes EOS for other destination systems requires cooperation. citeturn0search25turn1search6  

**Failure recovery flows (normative)**

Outbox publisher recovery:
- If app crashes after DB commit but before publish: outbox row remains PENDING → republished on restart.
- If app crashes after publish but before marking PUBLISHED: message may be duplicated; consumer dedup handles. citeturn1search2turn1search6  

Workflow step retry:
- If step fails with retryable error:
  - increment `attempt`
  - schedule retry via `workflow_timer` with backoff delay
  - preserve step execution history.

Saga compensation:
- If a step succeeded and later a downstream step fails irrecoverably, compensations execute in reverse completion order for steps that define compensation.
- Compensation failures:
  - default: mark workflow FAILED with `error_code=COMPENSATION_FAILED` and emit audit event.
  - optional config: retry compensations with bounded attempts.

**Public APIs and key classes for reliability modules (normative)**

Package `io.flowcore.tx`:
- `IdempotencyService`
  - `Optional<StoredResponse> find(scope, key, requestHash)`
  - `StoredResponse store(scope, key, requestHash, responseJson, expiresAt)`
- `InboxService`
  - `InboxAcceptResult tryAccept(source, messageId, consumerGroup)`
- `OutboxService`
  - `UUID enqueue(OutboxEventDraft draft)`
  - `List<OutboxEvent> fetchDueBatch(int batchSize)` (internal)
  - `void markPublished(UUID id, Instant publishedAt)`
  - `void markFailed(UUID id, FailureInfo info, Instant nextAttemptAt)`
- `OutboxPublisher` (interface)
  - `PublishResult publish(OutboxEvent event)`
- `OutboxPublisherScheduler`
  - periodic runner + backoff policy

Package `io.flowcore.kafka`:
- `KafkaEventPublisher`
  - `void publish(String topic, String key, Map<String,String> headers, byte[] payload)`
- `KafkaEosConfigurator`
  - applies `transactional.id`, `enable.idempotence`, `isolation.level=read_committed` when EOS enabled (implementation details vary by Spring Kafka). citeturn1search3turn0search25  

## Integrations, observability, and security

**Integration adapter contracts (normative)**

Provider calls must be abstracted behind `ProviderAdapter` to decouple workflow engine from HTTP/webhook realization.

Package `io.flowcore.integrations`:

```java
public interface ProviderAdapter<RequestT, ResponseT> {
  String providerName();                    // stable id, e.g. "mock-kyc"
  String operationName();                   // e.g. "verify"
  Class<RequestT> requestType();
  Class<ResponseT> responseType();

  ProviderCallResult<ResponseT> execute(ProviderCallContext ctx, RequestT request);
}
```

`ProviderCallContext` fields (DTO, public):
- `String correlationId`
- `String workflowType`
- `UUID workflowInstanceId`
- `String businessKey`
- `Map<String,String> headers` (propagated)
- `Duration deadline`
- `int attempt`

Result object:
- `status`: `SUCCESS | RETRYABLE_FAILURE | FATAL_FAILURE`
- `response`: optional
- `errorCode`, `errorDetail`
- `retryAfter`: optional for RETRYABLE_FAILURE

**HTTP/Webhook adapter requirements**
- REST client must:
  - support timeouts,
  - support retries with exponential backoff and jitter,
  - support circuit breaker and bulkhead.
- Webhook receiver must:
  - verify signature (RFC 9421) or HMAC variant,
  - enforce idempotency,
  - emit a normalized `WebhookReceived` event into the engine.

RFC 9421 defines standard signing/verification of HTTP message components; FlowCore’s webhook signing MUST follow it by default. citeturn2search3  

**Circuit breaker / retry/backoff**
- Use Resilience4j (direct) or Spring Cloud CircuitBreaker wrapper; both are acceptable if requirements are met. Spring Cloud CircuitBreaker explicitly provides Resilience4j implementations. citeturn7search1turn7search5turn7search0  
- Required defaults (configurable):
  - Circuit breaker window: 50 calls, failure rate threshold 50%, open state 30s.
  - Retry: max 3 attempts for provider calls (unless workflow retry policy defines otherwise); exponential backoff 200ms → 2s.
  - Time limiter: per-step timeout enforcement.

**WireMock stubs**
- For each provider, deliver a WireMock stub mapping set under `demo-*/src/test/resources/wiremock/*`.
- Tests must fail on unmatched requests by default (WireMock JUnit Jupiter extension behavior). citeturn3search3turn3search11turn3search7  

---

**Observability spec (normative)**

FlowCore must emit:
- application logs (JSON structured),
- Prometheus metrics,
- OpenTelemetry traces (OTLP export via collector).

Spring Boot observability uses Micrometer Observation; custom observations should use `ObservationRegistry`. citeturn2search0turn7search3  

OpenTelemetry semantic conventions must be followed for HTTP, messaging, and DB spans; trace semantic conventions define how operations are represented and attribute naming. citeturn0search2turn0search6turn0search34turn0search14  

Prometheus metric naming and label conventions must follow Prometheus recommended practices. citeturn0search3turn5search2  

Loki label best practices must be followed to avoid unbounded cardinality. citeturn0search7turn6search1  

Tempo local compose deployment and OTLP ingestion must follow Grafana Tempo documentation. citeturn6search2turn6search0  

**Tracing spans (required list)**
All spans must include:
- `service.name` resource attribute (demo-card-issuance / demo-payment-lifecycle)
- `flowcore.workflow.type`
- `flowcore.workflow.instance_id`
- `flowcore.business_key`
- correlation ids propagated to logs

Required spans:
- `flowcore.command` (root span)  
  Attributes: `flowcore.command.name`, `flowcore.command.source` (http|kafka|webhook), `flowcore.idempotency.key_present` (bool)
- `flowcore.workflow.transition`  
  Attributes: `flowcore.transition.id`, `flowcore.state.from`, `flowcore.state.to`, `flowcore.guard.evaluated` (bool)
- `flowcore.step.execute`  
  Attributes: `flowcore.step.id`, `flowcore.step.kind` (sync|async), `flowcore.step.attempt`
- `flowcore.outbox.enqueue` and `flowcore.outbox.publish`
- `flowcore.inbox.dedup_check`
- Provider spans (as child spans) should additionally use OTEL HTTP semantic conventions for outbound HTTP. citeturn0search2turn0search6  

**Metrics (required list and naming)**
Naming rules follow Prometheus conventions. citeturn0search3  

Metrics MUST be available on `/actuator/prometheus`.

Table of required FlowCore metrics:

| Metric name | Type | Required labels | Description |
|---|---|---|---|
| `flowcore_command_total` | counter | `command`, `source`, `result` | Count of commands processed |
| `flowcore_command_duration_seconds` | histogram | `command`, `source` | End-to-end command latency |
| `flowcore_workflow_instances` | gauge | `workflow_type`, `status` | Current instance counts by status |
| `flowcore_workflow_started_total` | counter | `workflow_type` | Workflows started |
| `flowcore_workflow_completed_total` | counter | `workflow_type` | Workflows completed successfully |
| `flowcore_workflow_failed_total` | counter | `workflow_type`, `reason` | Workflows failed |
| `flowcore_transition_total` | counter | `workflow_type`, `transition`, `result` | Transition attempts and outcomes |
| `flowcore_step_duration_seconds` | histogram | `workflow_type`, `step`, `result` | Step execution latency |
| `flowcore_step_retry_total` | counter | `workflow_type`, `step` | Retry count |
| `flowcore_step_timeout_total` | counter | `workflow_type`, `step` | Timeout occurrences |
| `flowcore_compensation_total` | counter | `workflow_type`, `step`, `result` | Compensation executions |
| `flowcore_outbox_pending` | gauge | `aggregate_type` | Pending outbox backlog |
| `flowcore_outbox_publish_total` | counter | `topic`, `result` | Outbox publish attempts |
| `flowcore_outbox_publish_duration_seconds` | histogram | `topic` | Publish latency |
| `flowcore_outbox_dead_total` | counter | `topic` | Events moved to DEAD |
| `flowcore_inbox_dedup_hits_total` | counter | `source`, `consumer_group` | Duplicate messages rejected |
| `flowcore_idempotency_replay_total` | counter | `scope` | Requests served from stored response |
| `flowcore_provider_call_total` | counter | `provider`, `operation`, `result` | Provider calls |
| `flowcore_provider_call_duration_seconds` | histogram | `provider`, `operation` | Provider call latency |
| `flowcore_authz_decision_total` | counter | `decision`, `action` | RBAC/ABAC decisions |
| `flowcore_security_signature_fail_total` | counter | `kind` | Webhook signature verification failures |
| `flowcore_timer_scheduled_total` | counter | `workflow_type`, `timer` | Timers scheduled |
| `flowcore_timer_fired_total` | counter | `workflow_type`, `timer` | Timers fired |

Metric label cardinality constraints (normative):
- `workflow_instance_id` must **never** be a label (too high cardinality).
- `error_detail` must never be a label; use log fields.
- For `topic`, use a bounded set of topic names.

This is consistent with Prometheus best practices on naming/labels. citeturn0search3  

**Logging format (required)**
- JSON structured logs with fields:
  - `timestamp`, `level`, `logger`, `thread`
  - `message`
  - `service`, `environment`
  - `trace_id`, `span_id`
  - `workflow_type`, `workflow_instance_id`, `business_key`
  - `command`, `step`, `transition`
  - `provider`, `operation`
  - `error_code` (if any)
- Sensitive fields must be masked (see security rules).
- Security logging guidance and separation between audit and operational logs must align with OWASP recommendations. citeturn3search5turn3search9  

**Grafana dashboards (required panels)**
Dashboards must be provisioned via file provisioning (GitOps-style). citeturn4search2turn4search6  

Deliver minimum dashboards:
- `flowcore-overview.json`
  - Command rate, error rate, p95 command latency
  - Workflow instance counts by status
  - Outbox backlog
- `flowcore-workflows.json`
  - Step latency heatmap by workflow type/step
  - Retry/timeout rate
  - Compensation executions
- `flowcore-kafka.json`
  - Publish/consume rates, error rate, timeouts
  - EOS enabled indicator (static panel from config)

**Prometheus scrape config (required)**
Prometheus configuration uses `scrape_configs` as described in Prometheus docs. citeturn5search2turn5search14  

Example `deploy/compose/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: "flowcore-demo-card"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["demo-card-issuance:8080"]

  - job_name: "flowcore-demo-payment"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["demo-payment-lifecycle:8081"]
```

**Tempo/Loki integration (required)**
- Use OTEL Collector pipelines to export:
  - traces to Tempo (OTLP)
  - logs to Loki (OTLP HTTP exporter as required by Grafana Loki docs)
- Grafana Tempo provides a documented local Docker Compose deployment approach. citeturn6search2turn6search1  

---

**Security spec (normative)**

**Authentication**
- JWT bearer tokens required for all non-public endpoints.
- JWT must be validated per RFC 7519:
  - required claims: `iss`, `sub`, `aud`, `exp`, `iat`
  - reject expired tokens; clock skew configurable
  - supported JOSE algorithms: `RS256` (required), `ES256` (optional). citeturn2search2turn2search10  

**Authorization**
- Hybrid RBAC + ABAC:
  - RBAC: roles determine coarse permissions.
  - ABAC: policies evaluate attributes of subject, object, environment; ABAC concepts aligned with NIST SP 800-162. citeturn3search0  

Policy engine (minimal required model):
- Subject attributes: `sub`, `roles[]`, `tenant`, `scopes[]`.
- Object attributes: `workflow_type`, `business_key`, `amount`, `currency`, `country`, `provider`.
- Environment attributes: `ip`, `time`, `risk_score` (optional).

Required actions (string constants):
- `workflow.start`, `workflow.signal`, `workflow.view`
- `card.issue`, `card.view`
- `payment.initiate`, `payment.capture`, `payment.refund`, `payment.view`
- `admin.metrics.view` (optional if metrics secured)

**Audit logging**
- Record every authz decision (ALLOW/DENY) into `flowcore.audit_log`.
- Audit records must include trace/span IDs when available to correlate with runtime events.

Security verification and logging controls should align with OWASP ASVS guidance on logging/monitoring and processing logs. citeturn3search1turn3search9turn3search5  

**Transport security**
- Local docker-compose: HTTP allowed for ease of demo.
- Production profile (documented): require TLS 1.2+; optionally mTLS between services.

**Webhook request signing**
- Must implement RFC 9421:
  - verify `Signature-Input` and `Signature` headers
  - enforce timestamp freshness (e.g., max skew 5 minutes)
  - include method, path, and selected headers in signature base
  - reject replay: store `(signature key id, signature params, digest)` in inbox dedup store. citeturn2search3turn2search15  

**Sensitive data masking (logs + events)**
- Must mask:
  - PAN-like numbers (even though cards are “virtual”, do not log them)
  - tokens, secrets, authorization headers
  - personal data fields: full name, document id, email (partial mask allowed)
- Masking rules:
  - last 4 digits visible for identifiers (if necessary)
  - otherwise replace with `***`.

## Testing, CI/CD, deployment, and demos

### Testing requirements (normative)

Test categories and required tooling:
- Unit tests: JUnit 5.
- Integration tests: Testcontainers for Postgres and Kafka.
- Adapter contract tests: WireMock JUnit Jupiter extension.
- Component/E2E tests: docker-compose up + scenario runner.
- Chaos/recovery tests: kill/restart during outbox publish and during workflow execution.

Testcontainers guidance:
- Use JUnit 5 integration patterns from Testcontainers docs. citeturn3search6turn3search22  
WireMock:
- Use JUnit Jupiter extension; fail on unmatched requests by default. citeturn3search3turn3search7  

**Example required test cases (with assertions)**

Unit tests:
- `WorkflowValidatorTest.unreachableStateRejected`  
  Assert: validator throws `WorkflowValidationException` with code `UNREACHABLE_STATE`.
- `GuardEvaluatorTest.jsonPathMissingIsFalse`  
  Assert: missing variable yields false unless `exists()` used.
- `RetryPolicyTest.exponentialBackoffIncreasesWithJitterBounded`  
  Assert: delay in `[expected*(1-jitter), expected*(1+jitter)]`.

Integration tests (Testcontainers):
- `OutboxPublisherIntegrationTest.publishesAndMarksPublished`  
  Setup: insert outbox event; run publisher; assert Kafka topic contains event; assert DB status PUBLISHED.
- `InboxDedupIntegrationTest.duplicateMessageNoSideEffects`  
  Setup: process same message twice; assert second returns DUPLICATE; assert workflow version increments only once.
- `SerializableInvariantTest.uniqueWorkflowInstanceTypeBusinessKey`  
  Setup: concurrent start commands; assert only one succeeds; the other receives 409 conflict or serialization retry path. (Postgres serializable semantics imply retry logic may be needed). citeturn1search0  

Contract tests (WireMock):
- `KycAdapterContractTest.verifyRequestShape`  
  Assert: outgoing request includes required headers (`Idempotency-Key`, `X-Correlation-Id`), expected JSON schema, and timeout.
- `WebhookVerifierTest.rfc9421RejectsReplay`  
  Assert: same signed webhook twice → second rejected; requires RFC 9421 verification. citeturn2search3  

Chaos/recovery tests:
- `CrashAfterDbCommitBeforePublish`  
  - Step: start workflow, ensure outbox row exists, kill app before publisher runs.
  - Restart app.
  - Assert: publisher eventually publishes event; workflow progresses; no double side-effects due to inbox/idempotency.
- `CrashAfterPublishBeforeMarkPublished`  
  - Simulate publish success but crash before DB update.
  - Assert: duplicate publish occurs; consumer dedup prevents double execution. citeturn1search2turn1search6  

**Required test pass rates and coverage**
- Unit test pass rate: 100% on main branch.
- Integration tests: 100% pass rate; flakes must be fixed (no rerun-to-pass policy).
- Coverage thresholds (JaCoCo):
  - Core modules (runtime/statemachine/tx/kafka/security/obs/integrations): **≥ 80% line coverage**
  - Demos: **≥ 60% line coverage**
- Mutation testing (optional): PIT for core modules, target ≥ 50% mutation coverage.

### CI/CD requirements (normative)

CI must run on pull requests and main branch using GitHub Actions.
- Setup Java with caching. citeturn4search1turn4search5  
- Quality gates:
  - `mvn -q -DskipTests=false verify`
  - Checkstyle + SpotBugs
  - Unit + integration tests
  - Coverage threshold enforcement
  - Build container images (multi-stage Dockerfile)
  - docker-compose smoke test: start stack and run a minimal scenario.

Example `.github/workflows/ci.yml` (skeleton):

```yaml
name: ci

on:
  pull_request:
  push:
    branches: [ "main" ]

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      # setup-java supports Maven caching per docs
      # and is recommended for Java workflows. 
      # (For primary source, see setup-java and GitHub docs.)
      - name: Build and test
        run: mvn -B -T 1C -DskipITs=false verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: "**/target/surefire-reports/*"
```

GitHub documents building/testing Java with Maven and dependency caching patterns. citeturn4search5turn4search1  

### Container build requirements (normative)

Dockerfile requirements:
- Multi-stage build, leaving only runtime artifacts in final image (smaller attack surface, per Docker guidance). citeturn5search0turn5search4  
- For Spring Boot apps, support layered jars or buildpacks; Dockerfile approach must be provided. Spring Boot documents efficient container images and layering. citeturn5search1turn5search19  

Example multi-stage Dockerfile for demo apps:

```dockerfile
# demo-card-issuance/Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY ../pom.xml /workspace/pom.xml
COPY ../flowcore-* /workspace/
COPY . /workspace/demo-card-issuance
RUN mvn -pl demo-card-issuance -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/demo-card-issuance/target/demo-card-issuance.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=2s --retries=10 CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Health endpoints and probe paths should align with Spring Boot Actuator liveness/readiness endpoints. citeturn2search1turn2search13  

### Deployment requirements (docker-compose) (normative)

Compose must support one-command startup:
- `docker compose --env-file deploy/compose/.env up --build`
- Compose dependency ordering must use healthchecks and service_healthy where supported; Docker documents startup/healthcheck-based dependency waiting. citeturn4search0  

Required services:
- demo-card-issuance app
- demo-payment-lifecycle app
- Postgres
- Kafka (KRaft or Zookeeper-based; no specific constraint)
- Prometheus
- Grafana
- Loki
- Tempo
- OTEL Collector

Tempo provides official local docker-compose deployment guidance. citeturn6search2  
Loki provides docs for running locally with Docker Compose and OTEL ingestion guidance. citeturn5search3turn6search1  

Example `deploy/compose/docker-compose.yml` (skeleton; ports are configurable and therefore “no specific constraint”):

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: flowcore
      POSTGRES_USER: flowcore
      POSTGRES_PASSWORD: flowcore
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U flowcore -d flowcore"]
      interval: 5s
      timeout: 2s
      retries: 20

  kafka:
    image: bitnami/kafka:latest
    # no specific constraint: distribution; must support transactions
    ports:
      - "${KAFKA_PORT:-9092}:9092"
    environment:
      # set to KRaft or ZK depending on image; keep configurable
      KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD-SHELL", "bash -c 'kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null 2>&1'"]
      interval: 10s
      timeout: 5s
      retries: 20

  tempo:
    image: grafana/tempo:latest
    command: ["-config.file=/etc/tempo.yml"]
    volumes:
      - ./tempo/tempo.yml:/etc/tempo.yml:ro
      - tempo-data:/var/tempo
    ports:
      - "${TEMPO_HTTP_PORT:-3200}:3200"
      - "${OTLP_GRPC_PORT:-4317}:4317"
      - "${OTLP_HTTP_PORT:-4318}:4318"

  loki:
    image: grafana/loki:latest
    command: ["-config.file=/etc/loki.yml"]
    volumes:
      - ./loki/loki.yml:/etc/loki.yml:ro
      - loki-data:/loki
    ports:
      - "${LOKI_PORT:-3100}:3100"

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    command: ["--config=/etc/otel-collector.yml"]
    volumes:
      - ./otel-collector/otel-collector.yml:/etc/otel-collector.yml:ro
    ports:
      - "${OTELCOL_OTLP_GRPC_PORT:-4317}:4317"
      - "${OTELCOL_OTLP_HTTP_PORT:-4318}:4318"
    depends_on:
      - tempo
      - loki

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "${PROMETHEUS_PORT:-9090}:9090"

  grafana:
    image: grafana/grafana:latest
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
    ports:
      - "${GRAFANA_PORT:-3000}:3000"
    depends_on:
      - prometheus
      - tempo
      - loki

  demo-card-issuance:
    build:
      context: ../
      dockerfile: demo-card-issuance/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      FLOWCORE_DB_URL: jdbc:postgresql://postgres:5432/flowcore
      FLOWCORE_DB_USER: flowcore
      FLOWCORE_DB_PASS: flowcore
      FLOWCORE_KAFKA_BOOTSTRAP: kafka:9092
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
    ports:
      - "${CARD_APP_PORT:-8080}:8080"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy

  demo-payment-lifecycle:
    build:
      context: ../
      dockerfile: demo-payment-lifecycle/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      FLOWCORE_DB_URL: jdbc:postgresql://postgres:5432/flowcore
      FLOWCORE_DB_USER: flowcore
      FLOWCORE_DB_PASS: flowcore
      FLOWCORE_KAFKA_BOOTSTRAP: kafka:9092
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
    ports:
      - "${PAYMENT_APP_PORT:-8081}:8081"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy

volumes:
  pgdata:
  tempo-data:
  loki-data:
```

Docker Compose startup order/healthcheck dependency behavior is documented by Docker. citeturn4search0turn4search4  

Grafana dashboards/datasources must be provisioned via Grafana provisioning. citeturn4search2turn4search6  

Tempo and Loki setup must follow their official docs and support OTLP pipelines. citeturn6search2turn6search1turn5search3turn4search7  

Sample `.env.example`:

```dotenv
POSTGRES_PORT=5432
KAFKA_PORT=9092
CARD_APP_PORT=8080
PAYMENT_APP_PORT=8081
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
LOKI_PORT=3100
TEMPO_HTTP_PORT=3200
OTLP_GRPC_PORT=4317
OTLP_HTTP_PORT=4318
```

### Demo apps (required APIs, payloads, workflows, seed data, runbook)

Both demos must expose:
- `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`. citeturn2search1turn2search13  
- OpenAPI spec files under `docs/openapi/*` (static YAML deliverables).

**Demo Card Issuance API (normative)**  
Base path: `/api/v1`

Endpoints:
- `POST /cards/issue`
  - Headers: `Authorization: Bearer <jwt>`, `Idempotency-Key: <key>`
  - Body:
    ```json
    {
      "userId": "u_123",
      "tenant": "demo",
      "cardProduct": "VISA_VIRTUAL",
      "country": "DE",
      "wallet": { "applePay": true, "googlePay": true }
    }
    ```
  - Response `202 Accepted`:
    ```json
    {
      "workflowInstanceId": "0f2e2b1e-0d69-4c3f-b4be-2b391d12a2a1",
      "businessKey": "cardapp:u_123",
      "status": "RUNNING",
      "currentState": "KYC_PENDING"
    }
    ```
- `GET /cards/applications/{workflowInstanceId}`
  - Response includes state, step history summary, and links to Grafana explore (optional).

Workflow definition deliverables:
- `demo-card-issuance/src/main/resources/workflows/card-issuance.yaml`
- A matching Java DSL version under `demo-card-issuance/src/main/java/.../CardIssuanceWorkflow.java`

Required steps:
- `KYC_CHECK` (async provider)
- `CARD_PROVISION` (async provider)
- `WALLET_BIND` (async provider)
- Terminal: `ACTIVE` or `FAILED`

Seed data:
- `migrations/V4__demo_seed_data.sql` inserts demo users and mock provider configs.

**Demo Payment Lifecycle API (normative)**  
Endpoints:
- `POST /payments`
  - Body:
    ```json
    {"merchantId":"m_1","amountMinor":1299,"currency":"EUR","paymentMethod":"TOKEN","customerId":"c_7"}
    ```
  - Response: `202 Accepted` with workflow instance.
- `POST /payments/{id}/capture`
- `POST /payments/{id}/refund`
- `GET /payments/{id}`

Workflow required states:
- `INITIATED → AUTHORIZED → CAPTURED → SETTLED`
- Refund path: `CAPTURED/SETTLED → REFUNDED`
- Failure paths: `FAILED`, plus compensations (e.g., void authorization).

**Runbook (step-by-step)**
Deliver `docs/runbook.md` with:
1. `docker compose up --build`
2. Verify:
   - Card demo readiness endpoint OK
   - Payment demo readiness endpoint OK
   - Prometheus targets UP
3. Trigger card issuance:
   - call `POST /cards/issue` with idempotency key
4. Observe:
   - Grafana dashboards show a new workflow start
   - Tempo traces show spans: `flowcore.command`, `flowcore.step.execute`, `flowcore.outbox.publish`
5. Trigger payment lifecycle:
   - `POST /payments`, then capture, then refund
6. Failure scenario:
   - configure provider stub to fail KYC; observe retries, then compensation, then FAILED state and audit entry.

### Non-functional requirements and targets (normative)

Runtime requirements:
- Java 21
- Spring Boot 3.x
- Kafka transactions support when EOS enabled (broker version must satisfy requirements stated by Spring Kafka). citeturn1search3  

Performance targets (measured locally on developer laptop; numbers are acceptance thresholds, not guarantees):
- Command execution (HTTP handler + DB transaction, excluding async provider latency):
  - p50 < 30 ms, p95 < 120 ms at 200 RPS with 50 concurrent clients
- Outbox publish loop:
  - sustain 1,000 events/sec publish throughput on local stack with p95 publish latency < 200 ms (from outbox row created → Kafka ack)
- Timer firing delay:
  - p95 timer fired within 250 ms of due time (with poll interval 500 ms and low contention)

Scalability guidelines:
- Engine is horizontally scalable:
  - Outbox publisher uses `SKIP LOCKED` to distribute work.
  - Timer poller uses locking to avoid double-fire.
  - Consumers are idempotent via inbox dedup. citeturn1search6turn1search16turn1search2  

Container resource defaults (configurable; “no specific constraint”):
- Each demo app: limit 512Mi–1Gi memory, 0.5–1 CPU
- Postgres: 1Gi memory
- Kafka: 1–2Gi memory (depends on image)

### Success criteria and acceptance metrics (normative)

**Required acceptance tests**
- Functional:
  - Card issuance happy path completes to ACTIVE within 30 seconds (with mock provider latencies).
  - Payment lifecycle happy path reaches SETTLED; refund completes to REFUNDED.
  - Failure path triggers retries then fails deterministically; compensation runs when configured.
- Reliability:
  - Duplicate HTTP requests with same idempotency key return identical response.
  - Duplicate Kafka messages do not cause duplicate side effects (validated via inbox hits and workflow version).
  - Crash/restart recovers without stuck workflows or lost outbox events.
- Observability:
  - Prometheus receives all required metrics.
  - Tempo contains required spans with workflow attributes.
  - Loki receives JSON logs with trace correlation fields.
- Security:
  - Invalid JWT rejected.
  - Unauthorized role denied and audit log records DENY.
  - Invalid RFC 9421 webhook signature rejected. citeturn2search2turn2search3turn3search0turn3search9  

**Numeric targets**
- Core module unit coverage ≥ 80%
- Integration + component tests: 100% pass on CI
- End-to-end scenario completion rate: ≥ 99% across 100 sequential runs in CI (allows 1 failure max; if failure occurs, it must be diagnosed as flake and fixed)
- Mean time to recovery (MTTR) after killing a demo app container: ≤ 20 seconds for workflows to resume progress after restart (local docker-compose)

### Comparison tables (required)

**Metrics coverage table**

| Category | Must include | Validation method |
|---|---|---|
| Commands | `flowcore_command_total`, `flowcore_command_duration_seconds` | Prometheus query + CI assertion |
| Workflows | started/completed/failed + instance gauges | Prometheus query |
| Steps | duration histogram + retry/timeout counters | Prometheus query |
| Messaging | outbox pending + publish totals + duration | Prometheus query + outbox backlog check |
| Security | authz decision counter + signature failures | Prometheus query + audit log rows |
| Timers | scheduled/fired counters | Prometheus query + timer tests |

**Test suites table**

| Suite | Tools | What it proves | Required pass level |
|---|---|---|---|
| Unit | JUnit 5 | DSL validation, guard evaluation, retry/backoff, policy evaluation | 100% |
| Integration | Testcontainers (Postgres, Kafka) | outbox/inbox/idempotency correctness, transactional behavior | 100% |
| Contract | WireMock | provider request/response shapes and signature handling | 100% |
| E2E | docker-compose + scenario runner | real stack works, dashboards wired | ≥ 99% completion |
| Chaos | kill/restart scripts | recovery invariants | 100% |

**CI gates table**

| Gate | Threshold | Tooling |
|---|---|---|
| Build | must pass | Maven |
| Static checks | must pass | Checkstyle, SpotBugs |
| Unit coverage | core ≥ 80% | JaCoCo |
| Integration tests | must pass | Surefire/Failsafe + Testcontainers |
| Container build | must pass | Docker multi-stage build citeturn5search0 |
| Compose smoke | must pass | docker compose + curl сценарий |
| Docs | must generate | Spring Modulith Documenter snippets citeturn0search8 |

## Acceptance checklist

**Repository and build**
- [ ] Maven multi-module project builds on Java 21 with `mvn verify`.
- [ ] Two demo apps run and expose readiness/liveness and Prometheus endpoints. citeturn2search1turn2search13  
- [ ] Docker multi-stage images build successfully. citeturn5search0turn5search4  

**Workflow engine**
- [ ] Java DSL and YAML DSL both supported; YAML validated at startup; invalid workflow rejected.
- [ ] Execution supports transitions, guards, retries, compensations, timeouts, timers, and fork/join.

**Data and reliability**
- [ ] Postgres schema and migrations present; uniqueness constraints enforce dedup/idempotency.
- [ ] Outbox written transactionally with workflow updates; polling publisher publishes to Kafka at-least-once; duplicates safe. citeturn1search2turn1search6turn1search1  
- [ ] Inbox dedup prevents duplicate side effects. citeturn1search6  
- [ ] Kafka EOS mode can be enabled and documented; consumer isolation read_committed is configured in that mode. citeturn1search3turn0search25  

**Integrations**
- [ ] ProviderAdapter contract implemented; retries/backoff + circuit breaker configured. citeturn7search0turn7search1  
- [ ] WireMock stubs and contract tests for providers exist. citeturn3search3turn3search11  

**Observability**
- [ ] Required spans emitted using OTEL conventions; traces exported to Tempo via OTEL Collector. citeturn0search2turn6search2  
- [ ] Required metrics exposed; naming/labels follow Prometheus practices. citeturn0search3turn5search2  
- [ ] JSON logs include trace correlation; logs shipped to Loki. citeturn6search1turn3search5  
- [ ] Grafana dashboards and datasources provisioned on startup. citeturn4search2turn4search6  

**Security**
- [ ] JWT validation per RFC 7519 with required claims. citeturn2search2  
- [ ] RBAC/ABAC enforced; audit logs recorded; OWASP logging guidance followed. citeturn3search0turn3search9turn3search5  
- [ ] Webhook signatures verified using RFC 9421; replay protection enabled. citeturn2search3  

**Demos and runbook**
- [ ] Card issuance demo completes happy path and failure path; documented runbook present.
- [ ] Payment lifecycle demo completes authorize→capture→settle→refund paths; documented runbook present.

**CI**
- [ ] GitHub Actions pipeline runs build/tests/coverage and uploads reports; uses setup-java caching. citeturn4search1turn4search5