package com.fraudguard.domain.store;

import com.fraudguard.exception.AmountSpikeSnapshotExpiredException;
import com.fraudguard.exception.EventPayloadMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class RedisStateStore implements StateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStateStore.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> slidingWindowScript;
    private final RedisScript<Long> recordAmountScript;
    private final RedisScript<List> amountSpikeScript;

    public RedisStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<Long> swScript = new DefaultRedisScript<>();
        swScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua")));
        swScript.setResultType(Long.class);
        this.slidingWindowScript = swScript;

        DefaultRedisScript<Long> raScript = new DefaultRedisScript<>();
        raScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/record_amount.lua")));
        raScript.setResultType(Long.class);
        this.recordAmountScript = raScript;
        DefaultRedisScript<List> asScript = new DefaultRedisScript<>();
        asScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/amount_spike.lua"))
        );
        asScript.setResultType(List.class);
        this.amountSpikeScript = asScript;
    }

    @Override
    public long recordAndCountSlidingWindow(String key, String member, long scoreMs, long windowMs, long ttlSeconds) {
        Long count = redisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(key),
                member,
                String.valueOf(scoreMs),
                String.valueOf(windowMs),
                String.valueOf(ttlSeconds)
        );
        return count != null ? count : 0L;
    }

    @Override
    public List<Long> getRecentAmounts(String key) {
        Set<String> entries = redisTemplate.opsForZSet().range(key, 0, -1);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> amounts = new ArrayList<>(entries.size());
        for (String entry : entries) {
            int colonIndex = entry.indexOf(':');
            if (colonIndex >= 0 && colonIndex < entry.length() - 1) {
                try {
                    amounts.add(Long.parseLong(entry.substring(colonIndex + 1)));
                } catch (NumberFormatException e) {
                    log.warn("Malformed amount history entry in key '{}': {}", key, entry);
                }
            }
        }
        return amounts;
    }

    @Override
    public void recordAmount(String key, String member, long amountPaise, long scoreMs, int maxHistory, long ttlSeconds) {
        try {
            redisTemplate.execute(
                    recordAmountScript,
                    Collections.singletonList(key),
                    member,
                    String.valueOf(amountPaise),
                    String.valueOf(scoreMs),
                    String.valueOf(maxHistory),
                    String.valueOf(ttlSeconds)
            );
        } catch (DataAccessException e) {
            Throwable root = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e);
            String msg = root != null && root.getMessage() != null ? root.getMessage() : e.getMessage();
            if (msg != null && msg.contains("EVENT_PAYLOAD_MISMATCH")) {
                throw new EventPayloadMismatchException(
                        "Conflicting transaction payload: event_id '" + member + "' was already evaluated with a different amount."
                );
            }
            throw e;
        }
    }

    @Override
    public List<Long> recordAndGetPriorAmounts(
            String key,
            String eventId,
            long amountPaise,
            long scoreMs,
            int historySize,
            int maxHistory,
            long ttlSeconds) {

        List<String> keys = List.of(
                key + ":history",
                key + ":snapshots",
                key + ":snapshot-index",
                key + ":snapshot-sequence"
        );

        try {
            List<?> result = redisTemplate.execute(
                    amountSpikeScript,
                    keys,
                    eventId,
                    String.valueOf(amountPaise),
                    String.valueOf(scoreMs),
                    String.valueOf(historySize),
                    String.valueOf(maxHistory),
                    String.valueOf(ttlSeconds)
            );

            if (result == null || result.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> amounts = new ArrayList<>(result.size());

            for (Object value : result) {
                if (value instanceof Number number) {
                    amounts.add(number.longValue());
                } else {
                    amounts.add(Long.parseLong(value.toString()));
                }
            }

            return List.copyOf(amounts);

        } catch (DataAccessException e) {
            Throwable root =
                    org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e);
            String message = root != null && root.getMessage() != null
                    ? root.getMessage()
                    : e.getMessage();

            if (message != null && message.contains("EVENT_PAYLOAD_MISMATCH")) {
                throw new EventPayloadMismatchException(
                        "Conflicting transaction payload: event_id '" + eventId
                                + "' was already evaluated with a different amount or timestamp."
                );
            }

            if (message != null && message.contains("AMOUNT_SPIKE_SNAPSHOT_EXPIRED")) {
                throw new AmountSpikeSnapshotExpiredException(
                        "Original amount spike evaluation snapshot has expired for event_id '"
                                + eventId + "'."
                );
            }

            throw e;
        }
    }
    @Override
    public boolean isHealthy() {
        try {
            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return false;
            }
            try (RedisConnection connection = connectionFactory.getConnection()) {
                String pong = connection.ping();
                return "PONG".equalsIgnoreCase(pong);
            }
        } catch (DataAccessException e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
}
