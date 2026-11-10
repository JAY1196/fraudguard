local key = KEYS[1]
local member_with_amount = ARGV[1]
local score = tonumber(ARGV[2])
local max_history = tonumber(ARGV[3])
local ttl_sec = tonumber(ARGV[4])

-- 1. Add current entry (eventId:amountPaise) with score = timestamp ms
redis.call('ZADD', key, score, member_with_amount)

-- 2. If cardinality exceeds max_history, trim oldest entries
local count = redis.call('ZCARD', key)
if count > max_history then
    redis.call('ZREMRANGEBYRANK', key, 0, count - max_history - 1)
end

-- 3. Set TTL
redis.call('EXPIRE', key, ttl_sec)

return redis.call('ZCARD', key)
