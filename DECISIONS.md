# Architecture Decision Records (ADRs) — FraudGuard

## ADR 001: Event Time vs Processing Time in Temporal Fraud Rules

### Context

Fraud detection rules inspect temporal patterns, such as transactions occurring during unusual overnight hours. In distributed financial architectures, the time an event occurred at the ledger or client (`transaction.timestamp`, Event Time) frequently differs from the time the fraud service processes the request (Processing Time), due to network latency, retry backoffs, or message queue delays.

### Decision

1. **Rule Evaluation on Event Time:** Time-dependent rules (such as `UnusualHoursHighAmountRule`) evaluate the transaction against `transaction.timestamp()` converted to the configured business timezone (`Asia/Kolkata` by default). Rules do not silently drop or mutate evaluations based on server wall-clock drift.

2. **API-Level Timestamp Boundary Validation:**
   - **Forward-Dating Protection (Clock Skew):** Transactions with timestamps ahead of the server `Clock` beyond a configurable skew (`maxFutureSkewSeconds`, default 300s / 5 minutes) are rejected immediately at the API perimeter with `HTTP 400` and error code `TIMESTAMP_OUT_OF_RANGE`. This prevents forward-dated transactions from bypassing future time-based validation.
   - **Historical Staleness & Replay Protection:** A configurable `maxPastAgeSeconds` threshold is provided at the API validator. It is **OFF by default** (`null`) so that:
     1. Offline reconciliations and backfilling transactions can be processed without rejection.
     2. Synthetic fraud simulators and test suites can replay recorded traffic across historical time horizons.
   - **Audit Timestamp:** Persisted decisions retain a distinct `evaluated_at` column capturing the processing time (`clock.instant()`).

### Trade-offs

- *Advantage:* Deterministic rule scoring; rejection is transparent to calling clients through RFC 9457 ProblemDetail rather than silent score corruption; supports historical replay testing.
- *Disadvantage:* Requires distributed clients to maintain reasonably synchronized clocks (NTP within 5 minutes).

---

## ADR 002: Strategy Pattern for Fraud Rule Execution

### Context

Fraud evaluation requires executing independent rules (high amount, unusual hours, velocity, rapid movement) against a single transaction payload.

### Decision

Model rules behind a unified `FraudRule` interface exposing `RuleResult evaluate(Transaction transaction)`. The `RuleEngine` collects all `FraudRule` implementations through Spring dependency injection.

### Why Not Chain of Responsibility?

Chain of Responsibility typically short-circuits on the first match. In fraud risk scoring, comprehensive evaluation requires running all enabled rules, accumulating weights, and generating an aggregated risk score (capped at 100) and reason codes.

---

## ADR 003: Clock Injection for Testability

### Context

Rules and validators dependent on system time often lead to flaky tests or require bytecode manipulation (e.g., PowerMock) when using `Instant.now()`.

### Decision

Inject `java.time.Clock` as a managed Spring bean (`ClockConfig`). The default runtime configuration uses `Clock.systemUTC()`, while unit and integration tests inject `Clock.fixed(...)`.

---

## ADR 004: Validated Configuration Properties Record

### Context

Rule thresholds, weights, and timezones must be configurable per deployment without recompilation, while preventing invalid configurations (such as negative weights or `reviewThreshold >= blockThreshold`) from silently corrupting scoring logic.

### Decision

Use a Java record bound to `@ConfigurationProperties(prefix = "fraudguard")` with `@Validated` and Jakarta Validation annotations, supplemented by compact-constructor invariants.

Invariants enforced at startup:

- `0 < review < block <= 100`
- `0 <= startHour, endHour <= 23`
- `1 <= weight <= 100`
- Valid IANA timezone identifier (`ZoneId.of(timeZone)`)

---

## ADR 005: Port Allocation Isolation

### Context

Development environments may run multiple backend services simultaneously (e.g., WalletLedger).

### Decision

Allocate non-overlapping ports:

- Application HTTP: `8082` (WalletLedger uses `8080`/`8081`)
- PostgreSQL: `127.0.0.1:5433` (standard default `5432`)
- Redis: `127.0.0.1:6381` (WalletLedger uses `6380`, standard default `6379`)

All infrastructure ports are bound to localhost (`127.0.0.1`).

---

## ADR 006: Windows Maven Wrapper Exit Code Handling

### Context

When running `.\mvnw.cmd clean verify` in PowerShell on Windows, Apache Maven Wrapper 3.3.4 returned `$LASTEXITCODE = 1` even when Maven reported `BUILD SUCCESS`. This was caused by the wrapper falling through the conditional block into `@echo Cannot start maven from wrapper >&2 && exit /b 1` when returning from the child batch script.

### Decision

Add explicit `@exit /b %ERRORLEVEL%` within the wrapper invocation block in `mvnw.cmd` so the process terminates with the exact exit code produced by Maven.

**Note:** This wrapper patch affects Windows local developer environments only; Linux CI environments invoke `./mvnw` with POSIX shell semantics.

---

## ADR 007: Event Contract Alignment with WalletLedger

### Context

FraudGuard evaluates transactions originating from the WalletLedger platform. The contracts must align so that ledger domain events map to fraud evaluation requests without intermediate transformations.

### Decision

1. **Naming:** Use snake_case JSON serialization matching ledger events (`event_id`, `source_wallet_id`, `destination_wallet_id`, `amount_paise`, `type`, `currency`, `timestamp`).

2. **Identifier Typing:** Enforce canonical UUID format for `event_id`, `source_wallet_id`, and `destination_wallet_id`.

3. **Transaction Types:** Restrict transaction classification to the canonical four ledger types: `TOPUP`, `TRANSFER`, `WITHDRAWAL`, `REVERSAL`.

4. **Currency Default:** Default `currency` to `INR` when omitted or blank.

5. **Error Contract:** Invalid UUID representations or unsupported transaction type strings return HTTP 400 with RFC 9457 ProblemDetail and code `VALIDATION_ERROR`.

