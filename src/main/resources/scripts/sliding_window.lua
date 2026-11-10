local key = KEYS[1]
local member = ARGV[1]
local score = tonumber(ARGV[2])
local window_ms = tonumber(ARGV[3])
local ttl_sec = tonumber(ARGV[4])

local min_score = score - window_ms

-- 1. Remove entries strictly older than the window boundary (never deletes newer state)
redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. min_score)

-- 2. Add or update current transaction event_id with score = event timestamp ms
redis.call('ZADD', key, score, member)

-- 3. Count entries strictly within [event_ts - window, event_ts]
local count = redis.call('ZCOUNT', key, min_score, score)

-- 4. Rely on TTL for idle key cleanup
redis.call('EXPIRE', key, ttl_sec)

return count
