# Architecture Decision Records (ADRs) — FraudGuard

## ADR 001: Event Time vs Processing Time in Temporal Fraud Rules

### Context
Fraud detection rules inspect temporal patterns (such as transactions occurring during unusual overnight hours). In distributed financial architectures, the time an event occurred at the ledger or client (`transaction.timestamp`, Event Time) frequently differs from the time the fraud service processes the request (`Processing Time`), due to network latency, retry backoffs, or message queue delays.

### Decision
1. **Rule Evaluation on Event Time**: Time-dependent rules (such as `UnusualHoursHighAmountRule`) evaluate the transaction against `transaction.timestamp()` converted to the configured business timezone (`Asia/Kolkata` by default). This guarantees evaluation determinism regardless of where the server is hosted or how long a message was in flight.
2. **Clock Injection for Processing-Time Guards**: The service injects a `java.time.Clock` bean strictly for processing-time enforcement:
   - **Future Clock Skew Guard**: Rejects timestamps where `eventTime > processingTime + maxFutureSkewSeconds` (default 300s). Protects against spoofed future timestamps intended to bypass time-of-day checks.
   - **Staleness Guard**: Rejects timestamps where `eventTime < processingTime - maxPastAgeSeconds` (default 86400s / 24h). Prevents replayed or ancient events from triggering anomalous time penalties.
   - **Audit Timestamp**: Used for recording `evaluated_at` processing time upon decision persistence.

### Trade-offs
- *Advantage*: Deterministic, replayable rule evaluation; testable via `Clock.fixed(...)`; robust against network lag.
- *Disadvantage*: Requires explicit bounds on acceptable clock drift between ledger and fraud service.

---

## ADR 002: Strategy Pattern for Fraud Rule Execution

### Context
Fraud evaluation requires executing independent rules (high amount, unusual hours, velocity, rapid movement) against a single transaction payload.

### Decision
Model rules behind a unified `FraudRule` interface exposing `RuleResult evaluate(Transaction transaction)`. The `RuleEngine` collects all `FraudRule` implementations via Spring dependency injection.

### Why Not Chain of Responsibility?
Chain of Responsibility typically short-circuits on the first match. In fraud risk scoring, comprehensive evaluation requires running all enabled rules, accumulating weights, and generating an aggregated risk score (capped at 100) and reason codes.

---

## ADR 003: Clock Injection for Testability

### Context
Rules dependent on system time often lead to flaky tests or require bytecode manipulation (e.g. PowerMock) when using `Instant.now()`.

### Decision
Inject `java.time.Clock` as a managed Spring bean (`ClockConfig`). Production uses `Clock.systemUTC()`, while unit and integration tests inject `Clock.fixed(...)`.

---

## ADR 004: Validated Configuration Properties Record

### Context
Rule thresholds, weights, and timezones must be configurable per deployment without re-compilation, while preventing invalid configurations (such as negative weights or `reviewThreshold >= blockThreshold`) from silently corrupting scoring logic.

### Decision
Use a Java record bound to `@ConfigurationProperties(prefix = "fraudguard")` with `@Validated` and Jakarta Validation annotations, supplemented with compact-constructor invariants.
Invariants enforced at startup:
- `0 < review < block <= 100`
- `0 <= startHour, endHour <= 23`
- `1 <= weight <= 100`
- Valid IANA Timezone identifier (`ZoneId.of(timeZone)`)

---

## ADR 005: Port Allocation Isolation

### Context
Dev environments may run multiple microservices simultaneously (e.g., WalletLedger).

### Decision
Allocate non-overlapping ports:
- Application HTTP: `8082` (WalletLedger uses `8080`/`8081`)
- PostgreSQL: `127.0.0.1:5433` (Standard default `5432`)
- Redis: `127.0.0.1:6381` (WalletLedger uses `6380`, standard default `6379`)
All ports are bound strictly to localhost (`127.0.0.1`).
