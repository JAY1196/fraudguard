local key = KEYS[1]
local event_id = ARGV[1]
local amount = tonumber(ARGV[2])
local score = tonumber(ARGV[3])
local max_history = tonumber(ARGV[4])
local ttl_sec = tonumber(ARGV[5])

-- 1. Check if event_id already exists in history
local entries = redis.call('ZRANGE', key, 0, -1)
for _, entry in ipairs(entries) do
    local colon_idx = string.find(entry, ":")
    if colon_idx then
        local existing_id = string.sub(entry, 1, colon_idx - 1)
        if existing_id == event_id then
            local existing_amount = tonumber(string.sub(entry, colon_idx + 1))
            if existing_amount ~= amount then
                return redis.error_reply("EVENT_PAYLOAD_MISMATCH: event_id " .. event_id .. " already evaluated with amount " .. existing_amount)
            end
            -- Identical payload replay: idempotent no-op
            return redis.call('ZCARD', key)
        end
    end
end

-- 2. Add current entry (event_id:amount) with score = timestamp ms
local member_with_amount = event_id .. ":" .. amount
redis.call('ZADD', key, score, member_with_amount)

-- 3. If cardinality exceeds max_history, trim oldest entries
local count = redis.call('ZCARD', key)
if count > max_history then
    redis.call('ZREMRANGEBYRANK', key, 0, count - max_history - 1)
end

-- 4. Refresh TTL for cleanup
redis.call('EXPIRE', key, ttl_sec)

return redis.call('ZCARD', key)
