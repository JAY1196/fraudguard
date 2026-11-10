package com.fraudguard.api.validator;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.exception.TimestampOutOfRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTimestampValidatorTest {

    private final Instant now = Instant.parse("2026-11-09T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

    private FraudGuardProperties buildProperties(long maxFutureSkew, Long maxPastAge) {
        return new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                null,
                new FraudGuardProperties.ValidationProperties(maxFutureSkew, maxPastAge)
        );
    }

    @Test
    @DisplayName("Should accept timestamp within allowed future skew (e.g. +60s <= +300s)")
    void shouldAcceptTimestampWithinFutureSkew() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, null)
        );

        assertThatCode(() -> validator.validate(now.plusSeconds(60)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject timestamp exceeding max allowed future skew (e.g. +301s > +300s)")
    void shouldRejectTimestampExceedingFutureSkew() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, null)
        );

        assertThatThrownBy(() -> validator.validate(now.plusSeconds(301)))
                .isInstanceOf(TimestampOutOfRangeException.class)
                .hasMessageContaining("Transaction timestamp is too far in the future");
    }

    @Test
    @DisplayName("Should accept ancient timestamp by default when maxPastAge is null (replay & simulator mode)")
    void shouldAcceptAncientTimestampByDefaultWhenMaxPastAgeIsNull() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, null)
        );

        // 30 days ago
        Instant ancientTimestamp = now.minusSeconds(30 * 86400L);
        assertThatCode(() -> validator.validate(ancientTimestamp))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept timestamp within maxPastAge when enabled")
    void shouldAcceptTimestampWithinMaxPastAgeWhenEnabled() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, 86400L) // 24h
        );

        // 1 hour ago
        assertThatCode(() -> validator.validate(now.minusSeconds(3600)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject timestamp older than maxPastAge when enabled")
    void shouldRejectTimestampOlderThanMaxPastAgeWhenEnabled() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, 86400L) // 24h
        );

        // 25 hours ago
        Instant staleTimestamp = now.minusSeconds(86400L + 3600);
        assertThatThrownBy(() -> validator.validate(staleTimestamp))
                .isInstanceOf(TimestampOutOfRangeException.class)
                .hasMessageContaining("Transaction timestamp is older than allowed maximum age");
    }

    @Test
    @DisplayName("Should safely ignore null timestamp")
    void shouldSafelyHandleNullTimestamp() {
        TransactionTimestampValidator validator = new TransactionTimestampValidator(
                fixedClock, buildProperties(300L, null)
        );

        assertThatCode(() -> validator.validate(null))
                .doesNotThrowAnyException();
    }
}
