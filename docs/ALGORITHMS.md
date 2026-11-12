# Rate-Limiting Algorithms

## 1. Implemented algorithm: Sliding-window log

FraudGuard currently implements a Redis sorted-set sliding-window log for velocity detection.

Each actor wallet has a key:

`fraudguard:velocity:<wallet-UUID>`

Each transaction is stored as:
- **Score:** transaction timestamp in epoch milliseconds.
- **Member:** transaction event UUID.

The Lua script in `src/main/resources/scripts/sliding_window.lua` executes these operations atomically:

1. `ZREMRANGEBYSCORE key -inf (min_score` removes entries strictly older than the window.
2. `ZADD key score member` records the current event.
3. `ZCOUNT key min_score score` counts events in the inclusive interval `[event_timestamp - window, event_timestamp]`.
4. `EXPIRE key ttl_seconds` sets the key's idle cleanup TTL.

The event UUID is the sorted-set member. Reusing the same member updates its score instead of creating a second member.

The configured velocity settings in `application.yml` are:
- Window: 300 seconds.
- Maximum transactions in the window: 5.
- Redis key TTL: 600 seconds.

The velocity rule triggers when the count returned by the script exceeds the configured maximum.

### Time complexity

Let:
- `N` = number of entries in the sorted set.
- `M` = number of entries removed by the pruning operation.

| Redis command | Complexity | Purpose |
|---|---:|---|
| `ZREMRANGEBYSCORE` | O(log N + M) | Remove expired events |
| `ZADD` | O(log N) per item | Insert or update event |
| `ZCOUNT` | O(log N) | Count events in score range |
| `EXPIRE` | O(1) | Set key expiration |

Combined per-event complexity: **O(log N + M)**.

This accounts for the four Redis operations in the Lua script. The pruning cost depends on the number of removed entries.

### Space complexity

Space complexity is **O(N) per active wallet**, where N is the number of retained event UUIDs in its sorted set.

Unlike counter-based algorithms, this implementation retains individual event records. Memory therefore grows with the number of events in the active window.

The 600-second TTL is an idle-key cleanup mechanism. It is not the five-minute velocity window.

## 2. Fixed-window counter

A fixed-window counter maintains one integer counter per wallet per fixed time bucket.

For a 60-second window, timestamps are assigned to buckets using:

`bucket = floor(timestamp / 60000)`

A typical Redis implementation uses `INCR` and `EXPIRE`.

### Time complexity

- `INCR`: O(1)
- `EXPIRE`: O(1)
- Combined: **O(1) per event**

### Space complexity

**O(1) per wallet per active bucket**, because only a counter is stored rather than individual events.

### Limitation: boundary burst

A fixed window resets its count at the bucket boundary. A client can use the remaining allowance immediately before the boundary and another full allowance immediately after it.

With a limit N, this can admit up to **2N events in a short interval** around the boundary.

## 3. Sliding-window counter

A sliding-window counter stores the current fixed-window count and the previous window's count.

It estimates the number of events in the rolling window by weighting the previous window according to how much of that window overlaps the current rolling interval.

A common estimate is:

`estimated_count = current_count + previous_count * (1 - elapsed_fraction)`

The request is admitted if the estimated count remains within the configured limit.

### Time complexity

A constant number of counter reads, increments and arithmetic operations gives **O(1) per event**.

### Space complexity

**O(1) per wallet**, because only a small fixed number of counters and window metadata are stored.

### Trade-off

This algorithm uses substantially less memory than a sliding-window log, but it is an approximation. It does not retain individual event timestamps and therefore cannot provide the same exact event-level count.

## 4. Token bucket

A token bucket stores:
- Current available tokens.
- Last refill timestamp.
- Configured bucket capacity.
- Configured refill rate.

On each request, elapsed time determines how many tokens should be replenished, up to the bucket capacity. The request consumes a token if one is available.

A Redis implementation can store the state in a hash and update it atomically using Lua.

Typical commands include `HGET`, `HSET` and `EXPIRE`.

### Time complexity

A constant number of hash operations and arithmetic operations gives **O(1) per event**, assuming a fixed number of fields.

### Space complexity

**O(1) per wallet**, because the bucket stores fixed-size state rather than individual transaction events.

### Trade-off

Token buckets control the average request rate while allowing bursts up to the configured capacity. They do not retain exact transaction history.

## 5. Algorithm comparison

| Algorithm | Time per event | Space per wallet | Exact event history? | Boundary behavior |
|---|---:|---:|---|---|
| Sliding-window log | O(log N + M) | O(N) | Yes | Enforces rolling-window count |
| Fixed-window counter | O(1) | O(1) | No | Can admit 2N around a boundary |
| Sliding-window counter | O(1) | O(1) | No | Approximate rolling-window count |
| Token bucket | O(1) | O(1) | No | Allows configured bursts |

The complexity figures above describe the stated Redis command patterns and constant-size algorithm state. They are not benchmark throughput claims.

## 6. Redis memory measurement

### Environment

Measurements were performed against the local Docker Redis container:

- Redis version: 7.4.12
- Architecture: 64-bit
- Sorted-set key format: `fraudguard:velocity:<wallet-UUID>`
- Member format: 36-character UUID
- Score: epoch timestamp in milliseconds
- Key TTL: 600 seconds
- Memory command: `MEMORY USAGE <key> SAMPLES 0`

The Redis configuration was checked directly:

| Setting | Measured value |
|---|---:|
| `zset-max-listpack-entries` | 128 |
| `zset-max-listpack-value` | 64 bytes |

### Measured results

| Entries | ZCARD | MEMORY USAGE | Bytes per entry | Encoding |
|---:|---:|---:|---:|---|
| 5 | 5 | 360 bytes | 72.00 | listpack |
| 50 | 50 | 2,664 bytes | 53.28 | listpack |
| 500 | 500 | 59,592 bytes | 119.18 | skiplist |

The 500-entry set exceeds the configured 128-entry listpack threshold and was observed using skiplist encoding.

Bytes per entry is calculated as measured key memory divided by sorted-set cardinality. It is not a universal per-entry allocation constant.

### Reproduction commands

The following PowerShell commands reproduce the measurement pattern. They create temporary keys using the application's key and member formats.

```powershell
$ErrorActionPreference = "Stop"
$container = "fraudguard-redis"

foreach ($count in @(5, 50, 500)) {
    $walletId = [guid]::NewGuid().ToString()
    $key = "fraudguard:velocity:$walletId"
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

    for ($i = 0; $i -lt $count; $i++) {
        $member = [guid]::NewGuid().ToString()
        docker exec $container redis-cli ZADD $key ($now + $i) $member | Out-Null
    }

    docker exec $container redis-cli EXPIRE $key 600 | Out-Null

    $cardinality = docker exec $container redis-cli ZCARD $key
    $memory = docker exec $container redis-cli MEMORY USAGE $key SAMPLES 0
    $encoding = docker exec $container redis-cli OBJECT ENCODING $key

    Write-Host "`n--- $count entries ---"
    Write-Host "ZCARD: $cardinality"
    Write-Host "Memory bytes: $memory"
    Write-Host "Bytes per entry: $([math]::Round(([double]$memory / $count), 2))"
    Write-Host "Encoding: $encoding"

    docker exec $container redis-cli DEL $key | Out-Null
}
```

### Measurement limitations

What was measured:
- Redis key memory reported by `MEMORY USAGE`.
- Sorted-set cardinality.
- Sorted-set encoding.
- One local Redis 7.4.12 instance.
- Three sample sizes: 5, 50 and 500 entries.

What was not measured:
- Total Redis process RSS.
- Production memory consumption across all wallets.
- Allocator fragmentation across the Redis process.
- Replication or persistence overhead.
- Memory under concurrent production traffic.
- A universal memory estimate for every wallet.

These results are local measurements, not production capacity benchmarks.

## 7. Boundary-burst demonstration

A Redis integration test was added to `RedisStateStoreTest`:

`shouldDemonstrateFixedWindowBoundaryBurst`

The test uses:
- Limit N = 5.
- Fixed-window duration = 60,000 milliseconds.
- Five events at 59,999 milliseconds.
- Five events at 60,000 milliseconds.
- The actual Redis Lua sliding-window implementation through `RedisStateStore`.

The fixed-window implementation uses a separate counter key for each time bucket. The sliding-window implementation uses the actual sorted-set Lua script.

### Exact captured test output

```text
Fixed window admitted: 10/10 events
Sliding window admitted: 5/10 events
```

Test execution result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This demonstrates the fixed-window boundary burst and the behavior of the project's actual sliding-window implementation for this test case. It is not a throughput benchmark.

## 8. Official Redis command references

Complexities are based on the official Redis command documentation:

- [ZADD](https://redis.io/docs/latest/commands/zadd/)
- [ZREMRANGEBYSCORE](https://redis.io/docs/latest/commands/zremrangebyscore/)
- [ZCOUNT](https://redis.io/docs/latest/commands/zcount/)
- [EXPIRE](https://redis.io/docs/latest/commands/expire/)
- [INCR](https://redis.io/docs/latest/commands/incr/)
- [SET](https://redis.io/docs/latest/commands/set/)
- [HGET](https://redis.io/docs/latest/commands/hget/)
- [HSET](https://redis.io/docs/latest/commands/hset/)
- [MEMORY USAGE](https://redis.io/docs/latest/commands/memory-usage/)
