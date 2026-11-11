package com.fraudguard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "fraudguard")
public record FraudGuardProperties(
        @Valid @NotNull ThresholdProperties thresholds,
        @Valid RulesProperties rules,
        @Valid ValidationProperties validation
) {
    public FraudGuardProperties {
        if (thresholds == null) {
            thresholds = new ThresholdProperties(40, 70);
        }
        if (rules == null) {
            rules = new RulesProperties(null, null, null);
        }
        if (validation == null) {
            validation = new ValidationProperties(300L, null);
        }
    }

    public record ThresholdProperties(
            @Min(1) @Max(99) int review,
            @Min(2) @Max(100) int block
    ) {
        public ThresholdProperties {
            if (review <= 0) {
                throw new IllegalArgumentException("Review threshold must be > 0 (got " + review + ")");
            }
            if (block <= review) {
                throw new IllegalArgumentException("Block threshold must be greater than review threshold (got review=" + review + ", block=" + block + ")");
            }
            if (block > 100) {
                throw new IllegalArgumentException("Block threshold must be <= 100 (got " + block + ")");
            }
        }
    }

    public record RulesProperties(
            @Valid HighAmountRuleProperties highAmount,
            @Valid UnusualHoursRuleProperties unusualHours,
            @Valid VelocityRuleProperties velocity
    ) {
        public RulesProperties {
            if (highAmount == null) {
                highAmount = new HighAmountRuleProperties(true, 5_000_000L, 50, "HIGH_AMOUNT");
            }
            if (unusualHours == null) {
                unusualHours = new UnusualHoursRuleProperties(
                        true, 23, 5, 2_000_000L, 40,
                        "UNUSUAL_HOURS_HIGH_AMOUNT", "Asia/Kolkata");
            }
            if (velocity == null) {
                velocity = new VelocityRuleProperties(
                        true, 300, 5, 40, "VELOCITY_EXCEEDED", 600);
            }
        }
    }

    public record HighAmountRuleProperties(
            boolean enabled,
            @Min(1) long thresholdPaise,
            @Min(1) @Max(100) int weight,
            @NotBlank String reasonCode
    ) {
        public HighAmountRuleProperties {
            if (weight < 1 || weight > 100) {
                throw new IllegalArgumentException("Weight must be between 1 and 100 (got " + weight + ")");
            }
            if (thresholdPaise < 1) {
                throw new IllegalArgumentException("Threshold paise must be >= 1 (got " + thresholdPaise + ")");
            }
        }
    }

    public record UnusualHoursRuleProperties(
            boolean enabled,
            @Min(0) @Max(23) int startHour,
            @Min(0) @Max(23) int endHour,
            @Min(1) long thresholdPaise,
            @Min(1) @Max(100) int weight,
            @NotBlank String reasonCode,
            @NotBlank String timeZone
    ) {
        public UnusualHoursRuleProperties {
            if (startHour < 0 || startHour > 23) {
                throw new IllegalArgumentException("Start hour must be between 0 and 23 (got " + startHour + ")");
            }
            if (endHour < 0 || endHour > 23) {
                throw new IllegalArgumentException("End hour must be between 0 and 23 (got " + endHour + ")");
            }
            if (weight < 1 || weight > 100) {
                throw new IllegalArgumentException("Weight must be between 1 and 100 (got " + weight + ")");
            }
            if (thresholdPaise < 1) {
                throw new IllegalArgumentException("Threshold paise must be >= 1 (got " + thresholdPaise + ")");
            }
            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException("TimeZone must not be blank");
            }
            try {
                ZoneId.of(timeZone);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timeZone: '" + timeZone + "'", e);
            }
        }
    }

    public record VelocityRuleProperties(
            boolean enabled,
            @Min(1) @Max(86400) int windowSeconds,
            @Min(1) int maxTransactionsInWindow,
            @Min(1) @Max(100) int weight,
            @NotBlank String reasonCode,
            @Min(1) long ttlSeconds
    ) {
        public VelocityRuleProperties {
            if (windowSeconds < 1 || windowSeconds > 86400) {
                throw new IllegalArgumentException("Velocity windowSeconds must be between 1 and 86400 (got " + windowSeconds + ")");
            }
            if (maxTransactionsInWindow < 1) {
                throw new IllegalArgumentException("Velocity maxTransactionsInWindow must be >= 1 (got " + maxTransactionsInWindow + ")");
            }
            if (weight < 1 || weight > 100) {
                throw new IllegalArgumentException("Velocity weight must be between 1 and 100 (got " + weight + ")");
            }
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("Velocity reasonCode must not be blank");
            }
            if (ttlSeconds < windowSeconds) {
                throw new IllegalArgumentException("Velocity ttlSeconds must be >= windowSeconds (got ttlSeconds=" + ttlSeconds + ", windowSeconds=" + windowSeconds + ")");
            }
        }
    }

    public record ValidationProperties(
            @Min(0) long maxFutureSkewSeconds,
            Long maxPastAgeSeconds
    ) {
        public ValidationProperties {
            if (maxFutureSkewSeconds < 0) {
                throw new IllegalArgumentException("maxFutureSkewSeconds must be >= 0 (got " + maxFutureSkewSeconds + ")");
            }
            if (maxPastAgeSeconds != null && maxPastAgeSeconds < 0) {
                throw new IllegalArgumentException("maxPastAgeSeconds must be >= 0 (got " + maxPastAgeSeconds + ")");
            }
        }
    }
}
