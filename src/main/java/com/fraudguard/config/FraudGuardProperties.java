package com.fraudguard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fraudguard")
public record FraudGuardProperties(
        @Valid @NotNull ThresholdProperties thresholds,
        @Valid RulesProperties rules
) {
    public FraudGuardProperties {
        if (thresholds == null) {
            thresholds = new ThresholdProperties(40, 70);
        }
        if (rules == null) {
            rules = new RulesProperties(null, null);
        }
    }

    public record ThresholdProperties(
            @Min(0) @Max(100) int review,
            @Min(0) @Max(100) int block
    ) {
        public ThresholdProperties {
            if (review > block) {
                throw new IllegalArgumentException("Review threshold cannot exceed block threshold");
            }
        }
    }

    public record RulesProperties(
            @Valid HighAmountRuleProperties highAmount,
            @Valid UnusualHoursRuleProperties unusualHours
    ) {
        public RulesProperties {
            if (highAmount == null) {
                highAmount = new HighAmountRuleProperties(true, 5_000_000L, 50, "HIGH_AMOUNT");
            }
            if (unusualHours == null) {
                unusualHours = new UnusualHoursRuleProperties(true, 23, 5, 2_000_000L, 40, "UNUSUAL_HOURS_HIGH_AMOUNT", "UTC");
            }
        }
    }

    public record HighAmountRuleProperties(
            boolean enabled,
            @Min(1) long thresholdPaise,
            @Min(0) @Max(100) int weight,
            @NotBlank String reasonCode
    ) {}

    public record UnusualHoursRuleProperties(
            boolean enabled,
            @Min(0) @Max(23) int startHour,
            @Min(0) @Max(23) int endHour,
            @Min(1) long thresholdPaise,
            @Min(0) @Max(100) int weight,
            @NotBlank String reasonCode,
            @NotBlank String timeZone
    ) {}
}
