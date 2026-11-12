package com.fraudguard.domain.store;

import java.util.List;

public interface StateStore {

    /**
     * Atomically slides the time window, prunes expired events, records the current event,
     * counts total events in the window, and refreshes key TTL in one atomic operation.
     *
     * @param key        the Redis sorted set key (e.g. fraudguard:velocity:{wallet_id})
     * @param member     the unique member identifier (e.g. event_id)
     * @param scoreMs    the event timestamp in epoch milliseconds (Event Time)
     * @param windowMs   the sliding window duration in milliseconds
     * @param ttlSeconds the key time-to-live in seconds
     * @return count of transactions in the window including this event
     */
    long recordAndCountSlidingWindow(String key, String member, long scoreMs, long windowMs, long ttlSeconds);

    /**
     * Retrieves recent transaction amounts for a wallet in chronological order.
     *
     * @param key the Redis key (e.g. fraudguard:amounts:{wallet_id})
     * @return list of previous amounts in paise
     */
    List<Long> getRecentAmounts(String key);

    /**
     * Records a transaction amount into the recent history, trims to maxHistory, and refreshes key TTL.
     *
     * @param key         the Redis key
     * @param member      the unique event identifier
     * @param amountPaise transaction amount in paise
     * @param scoreMs     event timestamp in epoch ms
     * @param maxHistory  maximum number of entries to retain
     * @param ttlSeconds  the key time-to-live in seconds
     */
    void recordAmount(String key, String member, long amountPaise, long scoreMs, int maxHistory, long ttlSeconds);

    /**
     * Atomically retrieves the prior transaction baseline and records the current event.
     * Replays return the original baseline snapshot.
     *
     * @param key amount-history Redis key
     * @param eventId unique event identifier
     * @param amountPaise current transaction amount
     * @param scoreMs event timestamp in epoch milliseconds
     * @param historySize maximum number of prior amounts to return
     * @param maxHistory maximum amount-history entries to retain
     * @param ttlSeconds Redis retention period
     * @return prior transaction amounts in chronological order
     */
    List<Long> recordAndGetPriorAmounts(
            String key,
            String eventId,
            long amountPaise,
            long scoreMs,
            int historySize,
            int maxHistory,
            long ttlSeconds
    );
    /**
     * Verifies store connectivity and operational status.
     *
     * @return true if store is reachable and operational, false otherwise
     */
    boolean isHealthy();
}
