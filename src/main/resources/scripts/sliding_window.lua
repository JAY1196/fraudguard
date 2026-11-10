local key = KEYS[1]
local member = ARGV[1]
local score = tonumber(ARGV[2])
local window_ms = tonumber(ARGV[3])
local ttl_sec = tonumber(ARGV[4])

local min_score = score - window_ms

-- 1. Remove entries strictly older than the window boundary
redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. min_score)

-- 2. Add or update current transaction event_id with score = event timestamp ms
redis.call('ZADD', key, score, member)

-- 3. Count members in the window
local count = redis.call('ZCARD', key)

-- 4. Set key TTL for idle expiration
redis.call('EXPIRE', key, ttl_sec)

return count
