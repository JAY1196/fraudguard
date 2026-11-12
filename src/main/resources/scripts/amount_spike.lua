local history_key = KEYS[1]
local snapshots_key = KEYS[2]
local snapshot_index_key = KEYS[3]
local snapshot_sequence_key = KEYS[4]

local event_id = ARGV[1]
local amount = ARGV[2]
local event_ts_text = ARGV[3]
local event_ts = tonumber(event_ts_text)
local history_size = tonumber(ARGV[4])
local max_history = tonumber(ARGV[5])
local ttl_sec = tonumber(ARGV[6])

if not event_id or event_id == ""
    or not amount or not string.match(amount, "^%d+$")
    or not event_ts
    or not string.match(event_ts_text, "^%d+$") then
    return redis.error_reply("INVALID_AMOUNT_OR_TIMESTAMP")
end

if not history_size or not max_history or not ttl_sec
    or history_size < 1
    or max_history < history_size
    or ttl_sec < 1 then
    return redis.error_reply("INVALID_ARGUMENTS")
end

-- 1. Exact replay returns its original baseline.
local existing = redis.call('HGET', snapshots_key, event_id)

if existing then
    local snapshot = cjson.decode(existing)

    if snapshot.amount ~= amount
        or snapshot.timestamp ~= event_ts_text then
        return redis.error_reply(
            'EVENT_PAYLOAD_MISMATCH: event_id ' .. event_id
        )
    end

    return snapshot.baseline
end

-- 2. Detect a retained history entry whose snapshot has expired.
local history_member = event_id .. ":" .. amount
local existing_score = redis.call('ZSCORE', history_key, history_member)

if existing_score then
    if tonumber(existing_score) ~= event_ts then
        return redis.error_reply(
            'EVENT_PAYLOAD_MISMATCH: event_id ' .. event_id
        )
    end

    return redis.error_reply("AMOUNT_SPIKE_SNAPSHOT_EXPIRED")
end

-- 3. Detect the same event ID with a different amount.
local matching_events = redis.call('ZRANGE', history_key, 0, -1)

for _, member in ipairs(matching_events) do
    local separator = string.find(member, ":")

    if separator and string.sub(member, 1, separator - 1) == event_id then
        return redis.error_reply(
            'EVENT_PAYLOAD_MISMATCH: event_id ' .. event_id
        )
    end
end

-- 4. Select prior events strictly earlier than this event.
local entries = redis.call(
    'ZRANGEBYSCORE',
    history_key,
    '-inf',
    '(' .. event_ts_text,
    'WITHSCORES'
)

local candidates = {}

for i = 1, #entries, 2 do
    local member = entries[i]
    local separator = string.find(member, ":")

    if separator then
        local prior_amount = string.sub(member, separator + 1)

        if string.match(prior_amount, "^%d+$") then
            table.insert(candidates, {
                amount = prior_amount,
                score = tonumber(entries[i + 1]),
                member = member
            })
        end
    end
end

-- 5. Sort newest first; use member as deterministic tie-breaker.
table.sort(candidates, function(a, b)
    if a.score == b.score then
        return a.member > b.member
    end
    return a.score > b.score
end)

local baseline = {}

for i = math.min(#candidates, history_size), 1, -1 do
    table.insert(baseline, candidates[i].amount)
end

-- 6. Persist the snapshot and record the current transaction.
local snapshot = cjson.encode({
    amount = amount,
    timestamp = event_ts_text,
    baseline = baseline
})

redis.call('HSET', snapshots_key, event_id, snapshot)
redis.call('ZADD', history_key, event_ts, history_member)

-- 7. Trim history by event timestamp.
local history_count = redis.call('ZCARD', history_key)
local history_excess = history_count - max_history

if history_excess > 0 then
    redis.call('ZREMRANGEBYRANK', history_key, 0, history_excess - 1)
end

-- 8. Use a monotonic sequence for deterministic snapshot ordering.
local sequence = redis.call('INCR', snapshot_sequence_key)

redis.call('ZADD', snapshot_index_key, sequence, event_id)

-- 9. Bound snapshot retention independently.
local snapshot_count = redis.call('ZCARD', snapshot_index_key)
local snapshot_excess = snapshot_count - max_history

if snapshot_excess > 0 then
    local expired_ids = redis.call(
        'ZRANGE', snapshot_index_key, 0, snapshot_excess - 1
    )

    for _, expired_id in ipairs(expired_ids) do
        redis.call('HDEL', snapshots_key, expired_id)
        redis.call('ZREM', snapshot_index_key, expired_id)
    end
end

-- 10. Refresh TTLs for all four keys.
redis.call('EXPIRE', history_key, ttl_sec)
redis.call('EXPIRE', snapshots_key, ttl_sec)
redis.call('EXPIRE', snapshot_index_key, ttl_sec)
redis.call('EXPIRE', snapshot_sequence_key, ttl_sec)

return baseline