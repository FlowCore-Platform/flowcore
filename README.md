# FlowCore

An open-source Java platform for building **stateful** business workflows whose execution remains **transactionally safe**, **auditable**, **observable**, and resilient under retries and partial failures.

## Quick Start

```bash
# Build
mvn verify

# Run the full stack (Postgres, Kafka, Prometheus, Grafana, Loki, Tempo)
docker compose --env-file deploy/compose/.env.example up --build
```

## Architecture

FlowCore uses a **validated state machine** as its core abstraction. Workflow instances progress via commands/signals and produce events, with optional asynchronous steps executed via Kafka-backed workers and provider adapters.

Key components:
- **Workflow Engine** — deterministic state transitions, durable execution, retries/backoff/timeouts
- **Transactional Layer** — outbox/inbox patterns, idempotency keys, saga compensation
- **Observability** — Prometheus metrics, OpenTelemetry traces, structured JSON logging
- **Security** — JWT authentication, RBAC/ABAC authorization, RFC 9421 webhook signing

### Module Overview

| Module | Description |
|--------|-------------|
| `flowcore-api` | Stable platform interfaces and DTOs |
| `flowcore-runtime` | Workflow engine, persistence, registry, query API |
| `flowcore-statemachine` | DSL model, validation, guard expressions, YAML parser |
| `flowcore-transaction` | Idempotency, inbox/outbox, saga log, timer scheduler |
| `flowcore-kafka` | Kafka producers/consumers, EOS configuration |
| `flowcore-observability` | Metrics/tracing conventions, logging correlation |
| `flowcore-security` | JWT authn, RBAC/ABAC, webhook verification, audit log |
| `flowcore-integrations` | Provider adapter contracts, HTTP client wrappers |
| `flowcore-starter` | Spring Boot auto-configuration starter |
| `demo-card-issuance` | Virtual card issuance workflow demo |
| `demo-payment-lifecycle` | Payment lifecycle workflow demo |

## Technology Stack

- Java 21
- Spring Boot 3.x
- PostgreSQL 16
- Apache Kafka
- OpenTelemetry / Prometheus / Grafana / Loki / Tempo
- Resilience4j
- Testcontainers / WireMock

## Demos

### Card Issuance
```bash
curl -X POST http://localhost:8080/api/v1/cards/issue \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-$(date +%s)" \
  -H "Authorization: Bearer <jwt>" \
  -d '{"userId":"u_123","tenant":"demo","cardProduct":"VISA_VIRTUAL","country":"DE"}'
```

### Payment Lifecycle
```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"m_1","amountMinor":1299,"currency":"EUR","paymentMethod":"TOKEN","customerId":"c_7"}'
```

See [docs/runbook.md](docs/runbook.md) for the full step-by-step guide.

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify -DskipITs=false

# Quality checks
mvn verify -P qa
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `flowcore.timer.poll-interval-ms` | 500 | Timer poll interval |
| `flowcore.outbox.batch-size` | 100 | Outbox publish batch size |
| `flowcore.kafka.eos-enabled` | false | Kafka exactly-once semantics |

## License

Apache License 2.0
