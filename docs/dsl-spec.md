# FlowCore DSL Specification

## Overview

FlowCore supports two ways to define workflows:
1. **Java DSL** — compile-time safety, IDE refactoring support
2. **YAML DSL** — ops-friendly, dynamic loading, easy to demo

## Java DSL

Package: `io.flowcore.statemachine.dsl`

### Example

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
  .build();
```

### Key Types

| Type | Purpose |
|------|---------|
| `WorkflowDsl` | Entry point for DSL |
| `WorkflowBuilder` | Fluent builder for workflow definitions |
| `StepBuilder` | Step configuration (activity, retry, timeout, compensation) |
| `RetryPolicy` | Retry configuration (fixed or exponential) |
| `TimeoutPolicy` | Timeout with fallback transition |
| `CompensationSpec` | Compensation step definition |
| `ForkSpec` / `JoinSpec` | Parallel branch definitions |

## YAML DSL

### Schema

```yaml
workflowType: string (^[a-zA-Z0-9_.-]{3,64}$)
version: integer
states: [string]
initialState: string
terminalStates: [string]

steps:
  - id: string
    kind: syncActivity | asyncActivity
    from: string
    to: string
    activity:
      adapter: string
      operation: string
    retry:
      mode: fixed | exponential
      maxAttempts: integer (>= 1)
      baseDelayMs: integer (>= 10)
      maxDelayMs: integer (>= baseDelayMs)
      jitterPct: integer (0-100)
    timeout:
      timeoutMs: integer (>= 100)
      onTimeoutState: string
    compensation:
      kind: syncActivity | asyncActivity
      activity: ...

transitions:
  - id: string
    from: string
    to: string
    trigger:
      type: event | command
      name: string
    guard: string (guard expression)
```

## Guard Expression Language

EBNF grammar:

```
guard        := orExpr ;
orExpr       := andExpr ( "||" andExpr )* ;
andExpr      := eqExpr ( "&&" eqExpr )* ;
eqExpr       := relExpr ( ("==" | "!=") relExpr )* ;
relExpr      := primary ((">" | ">=" | "<" | "<=") primary)* ;
primary      := literal | variable | functionCall | "(" guard ")" ;
literal      := "true" | "false" | number | string ;
variable     := "$." identifier ( "." identifier )* ;
functionCall := identifier "(" (guard ("," guard)*)? ")" ;
```

### Built-in Functions

| Function | Description |
|----------|-------------|
| `exists(x)` | Returns true if the variable path exists |
| `len(x)` | Returns length of array/string |
| `contains(a, b)` | Returns true if a contains b |
| `regex(s, pattern)` | Returns true if s matches regex pattern |

### Examples

```yaml
guard: '$.kyc.status == "APPROVED"'
guard: '$.amount > 10000 && $.currency == "EUR"'
guard: 'exists($.wallet.applePay) && $.kyc.score >= 80'
```

## Validation Rules

At workflow definition load time, the following are validated:
- `workflowType` format validation
- `initialState` must be in `states`
- All transition `from`/`to` states exist
- No unreachable states (BFS from initial)
- No dead-end non-terminal states
- Step IDs are unique
- Guard expressions compile
- Retry/timeout policy constraints
- Fork/join consistency
