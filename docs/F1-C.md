# F1-C: PostgreSQL Persistence, Idempotency, Security and Pagination

## Overview

F1-C extends FraudGuard from a rule-evaluation service with Redis-backed state into a service that also persists completed risk decisions in PostgreSQL.

The implementation covers:

1. PostgreSQL persistence and Flyway schema migrations.
2. Durable event idempotency using `event_id` and a normalized request hash.
3. API-key authentication for application endpoints.
4. Cursor-based pagination for stored risk decisions.

## Technology

- Java 21
- Spring Boot 4.1.2
- PostgreSQL 17
- Spring Data JPA
- Flyway
- Spring Security
- Redis 7
- Testcontainers
- Maven

## PostgreSQL persistence

The application uses PostgreSQL for durable risk-decision storage.

The main table is:

`risk_decisions`

The V1 Flyway migration creates the table and its constraints.

Important stored fields include:

- Database-generated decision ID.
- Unique event ID.
- SHA-256 request hash.
- Source and destination wallet IDs.
- Amount in paise.
- Transaction type and currency.
- Original event timestamp.
- Decision and score.
- JSONB reason codes.
- Degraded-mode indicator.
- Evaluation timestamp.

The decision ID is a generated `BIGINT` primary key. Event IDs are unique.

Hibernate schema generation is configured for validation rather than automatic schema creation. Flyway manages database migrations.

## Durable idempotency

The evaluation endpoint is:

`POST /api/v1/risk/evaluate`

The application calculates a deterministic SHA-256 hash from the normalized business request fields:

- `event_id`
- `source_wallet_id`
- `destination_wallet_id`
- `amount_paise`
- `type`
- `currency`
- `timestamp`

Currency is normalized before hashing. Missing or blank currency defaults to `INR`.

The application attempts to claim the event ID using a PostgreSQL insert with conflict handling.

Expected behavior:

| Scenario | Result |
| --- | --- |
| New event ID | Evaluate and persist a decision |
| Same event ID and same payload | Return the previously stored decision |
| Same event ID and changed payload | HTTP 422 |
| Evaluation failure before transaction commit | Roll back the incomplete claim |

The database unique constraint and transactional claim prevent duplicate event processing from creating multiple persisted decisions for the same event ID.

A payload mismatch is represented by `EventPayloadMismatchException` and the `EVENT_PAYLOAD_MISMATCH` error code.

## API-key authentication

FraudGuard uses a stateless API-key authentication filter.

| Property | Value |
| --- | --- |
| Header | `X-API-Key` |
| Configuration property | `fraudguard.security.api-key` |
| Environment variable | `FRAUDGUARD_API_KEY` |
| Missing or invalid key | HTTP 401 |
| Public endpoint | `/actuator/health` and its subpaths |

Both configured and supplied keys are SHA-256 hashed before constant-time comparison.

The API key must contain at least 32 characters. Missing or invalid key configuration prevents application startup.

CSRF, form login, HTTP Basic authentication and logout are disabled for this stateless API security configuration.

Swagger UI and `/v3/api-docs` are public in the dev profile. OpenAPI declares the `X-API-Key` API-key security scheme.

Never commit a real API key. Use an environment variable or a secret manager in deployed environments.

## Wallet-scoped decisions pagination

The endpoint is `GET /api/v1/wallets/{wallet_id}/decisions`.

It returns persisted decisions where the wallet is either the source or destination. Self-transfers appear only once.

### Query parameters

| Parameter | Default | Constraints |
| --- | --- | --- |
| `limit` | 20 | 1 through 100 |
| `cursor` | No cursor | Opaque, versioned Base64 cursor |

Results use descending decision ID keyset pagination. Source and destination query results are merged and deduplicated. Each branch fetches `limit + 1` rows to determine whether another page exists. No total-count query is required.

Clients must treat the cursor as opaque and pass the returned value unchanged.

### Example

```http
GET /api/v1/wallets/11111111-1111-1111-1111-111111111111/decisions?limit=20
X-API-Key: your-api-key
```
Example response:

```json
{
  "decisions": [],
  "next_cursor": "djE6MTIz",
  "has_more": true
}
```
Use the returned `next_cursor` unchanged for the next request. Invalid limits and cursors return HTTP 400.

### Query indexes

V2 creates descending indexes on `(source_wallet_id, id DESC)` and `(destination_wallet_id, id DESC)`.

The PostgreSQL query-plan integration test verifies index usage for both query branches without Sort nodes.

## Configuration

The default local development configuration uses:

| Setting | Default |
| --- | --- |
| Application port | 8082 |
| PostgreSQL host | 127.0.0.1 |
| PostgreSQL port | 5433 |
| PostgreSQL database | fraudguard |
| Redis host | 127.0.0.1 |
| Redis port | 6381 |

Relevant environment variables:

- `APP_PORT`
- `POSTGRES_HOST`
- `POSTGRES_PORT`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `FRAUDGUARD_API_KEY`

Docker Compose starts PostgreSQL 17 and Redis 7. PostgreSQL and Redis are bound to localhost.

### PowerShell API key setup

Set the key in the same terminal session used to launch Spring Boot:

```powershell
$env:FRAUDGUARD_API_KEY = "replace-with-a-long-random-secret"
.\mvnw.cmd spring-boot:run
```

The `.env.example` file documents configuration values for local development and Docker Compose. Spring Boot does not automatically load `.env` when launched directly through Maven.

## Local verification

Run the full Maven verification suite:

```powershell
.\mvnw.cmd clean verify
```

The F1-C implementation was verified as part of a full regression run with:

- 161 tests.
- 0 failures.
- 0 errors.
- 0 skipped tests.

## Scope boundaries

F1-C provides durable risk-decision persistence and query access. It does not add Kafka ingestion or a machine-learning fraud classifier.

Kafka integration remains planned for F1-D.
