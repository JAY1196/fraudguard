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


---

## ADR 008: StateStore Abstraction, Atomic Sliding Windows, and Out-of-Order Semantics

### Context
Stateful fraud detection rules (such as transaction velocity in a sliding window and recent amount spike detection) require storing and querying historical events per wallet. Directly coupling fraud rules to `StringRedisTemplate` harms unit-testability, while issuing multi-step sequential Redis network calls creates race conditions and network round-trips. Furthermore, distributed transactions can arrive out-of-order due to network jitter.

### Decision
1. **Domain Abstraction (`StateStore`)**: Define an interface isolating all persistence and window operations from rule logic. Rules collaborate exclusively with `StateStore`, enabling 100% Mockito unit tests without spinning up Docker/Redis.
2. **Single-Roundtrip Atomic Lua Execution (`RedisStateStore`)**:
   - `sliding_window.lua` executes `ZREMRANGEBYSCORE`, `ZADD`, `ZCOUNT`, and `EXPIRE` atomically within Redis server single-threaded execution context in one round-trip.
   - `record_amount.lua` executes `ZADD`, `ZREMRANGEBYRANK`, and `EXPIRE` atomically to maintain a bounded chronological history of recent amounts.
3. **Out-of-Order Semantics & Interval Counting**:
   - Window count is computed strictly across `[event_ts - window, event_ts]` via `ZCOUNT key (event_ts - window) event_ts` where the displayed parentheses denote arithmetic grouping, not an exclusive Redis score bound. The Lua script passes `min_score` and `event_ts` as inclusive bounds.
   - An out-of-order late event counts strictly the events that occurred up to its own timestamp; it does not count events with timestamps in its future.
   - Older events **never delete newer state**: `ZREMRANGEBYSCORE key -inf (event_ts - window)` only prunes items strictly before the window; any items with `timestamp >= event_ts` remain untouched.
   - Cleanup of dormant keys relies on atomic `EXPIRE key ttl_sec` refreshed on every evaluation.
4. **Idempotency & Deduplication**: Event IDs serve as sorted set members; re-evaluating the same event ID updates the member score in-place without incrementing cardinality.
5. **Memory Safety**: Every state mutation sets an idle TTL on the key, ensuring dormant wallet keys are automatically collected by Redis.

### Algorithm Trade-offs and Measurement

The selected sliding-window log retains individual event timestamps in a Redis sorted set. Its per-event command complexity is O(log N + M), where N is the sorted-set cardinality and M is the number of expired entries removed during pruning. Its state is O(N) per wallet.

Fixed-window counters, sliding-window counters, and token buckets use constant-size state and O(1) operations for their typical Redis command patterns, but have different accuracy and burst semantics. The fixed-window boundary-burst limitation and the trade-offs are documented in [docs/ALGORITHMS.md](docs/ALGORITHMS.md).

A local Redis 7.4.12 measurement using the application's key format, UUID members, and 600-second TTL reported:
- 5 entries: 360 bytes (listpack).
- 50 entries: 2,664 bytes (listpack).
- 500 entries: 59,592 bytes (skiplist).

These are measurements of individual Redis keys using `MEMORY USAGE ... SAMPLES 0`; they are not production-wide memory or throughput benchmarks. The boundary-burst integration test admitted 10/10 events with a fixed-window counter and 5/10 with the actual sliding-window Lua implementation. Exact output and reproduction details are in [docs/ALGORITHMS.md](docs/ALGORITHMS.md).

---

## ADR 009: Conflict Detection on Event ID Mutation

### Context
In distributed financial architectures, an `event_id` is a globally unique, immutable identifier representing a specific business event. What should happen if an evaluation request arrives with an already-recorded `event_id` but with a conflicting amount?

### Decision
1. **Rejection with HTTP 400**: Re-submitting an existing `event_id` with a different transaction amount is treated as an integrity conflict and rejected immediately with `HTTP 400 Bad Request`, RFC 9457 `ProblemDetail`, and error code `EVENT_PAYLOAD_MISMATCH`.
2. **Rationale**:
   - Silently ignoring the new amount or updating the historical baseline risks accepting corrupted or tampered ledger entries.
   - Rejecting with `EVENT_PAYLOAD_MISMATCH` alerts the upstream caller (WalletLedger) to a serious payload divergence or identifier collision.
3. **Idempotent Replay**: Re-submitting the exact same `event_id` with an identical amount is an idempotent no-op and succeeds without error or duplicate history entry.



## ADR 010: PostgreSQL Transactions, Redis State, and Degraded Decision Replays

### Context

FraudGuard persists risk decisions and idempotency claims in PostgreSQL, while stateful fraud rules use Redis for velocity windows and amount-spike history. PostgreSQL and Redis do not participate in a shared distributed transaction. A Redis mutation can therefore succeed even if the surrounding PostgreSQL transaction later rolls back.

Redis availability can also affect evaluation. When a Redis-backed rule encounters a Redis connection failure or query timeout, the RuleEngine continues evaluating available rules and marks the resulting decision as degraded.

### Decision

1. **PostgreSQL is the durable decision and idempotency record.**
   - The event claim and completed risk decision are written within the PostgreSQL transaction.
   - An identical retry retrieves the previously stored decision instead of running the rules again.
   - Reusing an event ID with a different request payload is rejected as an event payload mismatch.

2. **Redis mutations are not transactionally coupled to PostgreSQL.**
   - Redis Lua scripts execute their state mutations atomically within Redis, but they cannot roll back when the PostgreSQL transaction rolls back.
   - A Redis mutation may therefore remain after a failed PostgreSQL transaction.
   - FraudGuard does not claim cross-datastore atomicity.

3. **Event-ID-keyed Redis state makes replays safe.**
   - The velocity sliding-window script uses the event ID as its sorted-set member. Replaying the same event updates that member rather than creating another entry.
   - The amount-spike script stores a baseline snapshot keyed by event ID. An exact replay returns the original baseline instead of recording the event again.
   - These mechanisms make repeated processing of the same event idempotent within the retained Redis state.
   - Amount-spike snapshots have bounded retention. If a required snapshot has expired while its history entry remains, the script reports `AMOUNT_SPIKE_SNAPSHOT_EXPIRED` rather than silently calculating a different baseline.

4. **Degraded decisions are persisted and remain authoritative for retries.**
   - When a Redis connection failure or query timeout prevents a Redis-backed rule from running, the RuleEngine continues with the rules it can evaluate.
   - The resulting decision is marked `degraded=true` and includes the `DEGRADED` reason code.
   - This completed decision is stored in PostgreSQL.
   - An identical retry returns that stored decision, including its original degraded flag and score, even if Redis has recovered since the first evaluation.
   - A retry does not silently recalculate or upgrade the original decision. A new evaluation requires a distinct event ID.

### Trade-offs

- **Advantage:** PostgreSQL provides a durable, consistent idempotency record, while event-ID-based Redis operations reduce duplicate state mutations during retries.
- **Advantage:** A degraded result remains explainable and stable across retries instead of changing based on Redis availability at retry time.
- **Disadvantage:** Redis state can outlive a rolled-back PostgreSQL transaction, and bounded Redis retention means replay safety is limited by the lifetime of the relevant state.
- **Disadvantage:** A degraded decision may remain stored even after Redis recovers; automatic re-evaluation is intentionally not performed.