package com.fraudguard.api.validator;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.exception.TimestampOutOfRangeException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class TransactionTimestampValidator {

    private final Clock clock;
    private final FraudGuardProperties properties;

    public TransactionTimestampValidator(Clock clock, FraudGuardProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void validate(Instant timestamp) {
        if (timestamp == null) {
            return;
        }

        Instant now = clock.instant();
        var validationConfig = properties != null ? properties.validation() : null;
        long maxFutureSkewSeconds = validationConfig != null ? validationConfig.maxFutureSkewSeconds() : 300L;
        Long maxPastAgeSeconds = validationConfig != null ? validationConfig.maxPastAgeSeconds() : null;

        if (timestamp.isAfter(now.plusSeconds(maxFutureSkewSeconds))) {
            throw new TimestampOutOfRangeException(
                    "Transaction timestamp is too far in the future: " + timestamp
                            + " (processing time: " + now + ", max future skew: " + maxFutureSkewSeconds + "s)"
            );
        }

        if (maxPastAgeSeconds != null && maxPastAgeSeconds > 0) {
            if (timestamp.isBefore(now.minusSeconds(maxPastAgeSeconds))) {
                throw new TimestampOutOfRangeException(
                        "Transaction timestamp is older than allowed maximum age: " + timestamp
                                + " (processing time: " + now + ", max past age: " + maxPastAgeSeconds + "s)"
                );
            }
        }
    }
}
