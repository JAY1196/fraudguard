package com.fraudguard.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudGuardPropertiesTest {

    private final ApplicationContextRunner amountSpikeContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AmountSpikePropertiesConfiguration.class)
                    .withPropertyValues(
                            "fraudguard.rules.amount-spike.enabled=true",
                            "fraudguard.rules.amount-spike.multiplier=3.0",
                            "fraudguard.rules.amount-spike.history-size=5",
                            "fraudguard.rules.amount-spike.minimum-history=3",
                            "fraudguard.rules.amount-spike.weight=35",
                            "fraudguard.rules.amount-spike.reason-code=AMOUNT_SPIKE",
                            "fraudguard.rules.amount-spike.max-history=100",
                            "fraudguard.rules.amount-spike.ttl-seconds=86400"
                    );

    @Test
    @DisplayName("Default properties must use 40 review, 70 block, Asia/Kolkata timezone, and 300s future skew")
    void shouldHaveSensibleDefaults() {
        FraudGuardProperties properties = new FraudGuardProperties(null, null, null);

        assertThat(properties.thresholds().review()).isEqualTo(40);
        assertThat(properties.thresholds().block()).isEqualTo(70);
        assertThat(properties.rules().unusualHours().timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(properties.rules().unusualHours().startHour()).isEqualTo(23);
        assertThat(properties.rules().unusualHours().endHour()).isEqualTo(5);
        assertThat(properties.rules().highAmount().weight()).isEqualTo(50);
        assertThat(properties.rules().unusualHours().weight()).isEqualTo(40);
        assertThat(properties.validation().maxFutureSkewSeconds()).isEqualTo(300L);
        assertThat(properties.validation().maxPastAgeSeconds()).isNull();
    }

    @Test
    @DisplayName("Threshold validation: block must be strictly greater than review")
    void shouldFailWhenBlockIsLessThanOrEqualToReview() {
        // block == review
        assertThatThrownBy(() -> new FraudGuardProperties.ThresholdProperties(50, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block threshold must be greater than review threshold");

        // block < review
        assertThatThrownBy(() -> new FraudGuardProperties.ThresholdProperties(60, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block threshold must be greater than review threshold");
    }

    @Test
    @DisplayName("Threshold validation: review must be > 0 and block must be <= 100")
    void shouldFailWhenThresholdsAreOutOfBounds() {
        assertThatThrownBy(() -> new FraudGuardProperties.ThresholdProperties(0, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Review threshold must be > 0");

        assertThatThrownBy(() -> new FraudGuardProperties.ThresholdProperties(40, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block threshold must be <= 100");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 24, 25})
    @DisplayName("Hour validation: startHour must be between 0 and 23")
    void shouldFailWhenStartHourIsInvalid(int invalidHour) {
        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, invalidHour, 5, 2_000_000L, 40, "CODE", "Asia/Kolkata"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start hour must be between 0 and 23");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 24, 25})
    @DisplayName("Hour validation: endHour must be between 0 and 23")
    void shouldFailWhenEndHourIsInvalid(int invalidHour) {
        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, 23, invalidHour, 2_000_000L, 40, "CODE", "Asia/Kolkata"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End hour must be between 0 and 23");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 101, 150})
    @DisplayName("Weight validation: weights must be between 1 and 100")
    void shouldFailWhenWeightsAreOutOfRange(int invalidWeight) {
        assertThatThrownBy(() -> new FraudGuardProperties.HighAmountRuleProperties(
                true, 5_000_000L, invalidWeight, "HIGH_AMOUNT"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight must be between 1 and 100");

        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, 23, 5, 2_000_000L, invalidWeight, "CODE", "Asia/Kolkata"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight must be between 1 and 100");
    }

    @Test
    @DisplayName("Timezone validation: invalid time zone string must fail fast")
    void shouldFailWhenTimezoneIsInvalid() {
        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, 23, 5, 2_000_000L, 40, "CODE", "Invalid/Fake_Zone"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeZone: 'Invalid/Fake_Zone'");
    }

    @Test
    @DisplayName("Validation properties: negative maxFutureSkewSeconds or negative maxPastAgeSeconds must fail")
    void shouldFailWhenValidationPropertiesAreNegative() {
        assertThatThrownBy(() -> new FraudGuardProperties.ValidationProperties(-1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxFutureSkewSeconds must be >= 0");

        assertThatThrownBy(() -> new FraudGuardProperties.ValidationProperties(300L, -10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPastAgeSeconds must be >= 0");
    }

    @Test
    @DisplayName("Velocity defaults must use 300-second window, max 5 transactions, weight 40 and TTL 600")
    void shouldHaveSensibleVelocityDefaults() {
        FraudGuardProperties properties = new FraudGuardProperties(null, null, null);
        var velocity = properties.rules().velocity();

        assertThat(velocity.enabled()).isTrue();
        assertThat(velocity.windowSeconds()).isEqualTo(300);
        assertThat(velocity.maxTransactionsInWindow()).isEqualTo(5);
        assertThat(velocity.weight()).isEqualTo(40);
        assertThat(velocity.reasonCode()).isEqualTo("VELOCITY_EXCEEDED");
        assertThat(velocity.ttlSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("Velocity window must be between 1 and 86400 seconds")
    void shouldRejectInvalidVelocityWindow() {
        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 0, 5, 40, "VELOCITY_EXCEEDED", 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 86400");

        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 86401, 5, 40, "VELOCITY_EXCEEDED", 86401))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 86400");
    }

    @Test
    @DisplayName("Velocity maximum transactions must be at least 1")
    void shouldRejectInvalidVelocityMaximumTransactions() {
        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 0, 40, "VELOCITY_EXCEEDED", 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTransactionsInWindow must be >= 1");
    }

    @Test
    @DisplayName("Velocity weight must be between 1 and 100")
    void shouldRejectInvalidVelocityWeight() {
        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 5, 0, "VELOCITY_EXCEEDED", 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight must be between 1 and 100");

        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 5, 101, "VELOCITY_EXCEEDED", 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight must be between 1 and 100");
    }

    @Test
    @DisplayName("Velocity reason code must not be blank")
    void shouldRejectBlankVelocityReasonCode() {
        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 5, 40, " ", 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode must not be blank");
    }

    @Test
    @DisplayName("Velocity TTL must be greater than or equal to window duration")
    void shouldRejectVelocityTtlShorterThanWindow() {
        assertThatThrownBy(() -> new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 5, 40, "VELOCITY_EXCEEDED", 299))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttlSeconds must be >= windowSeconds");
    }

    @Test
    @DisplayName("Velocity TTL equal to window duration is valid")
    void shouldAcceptVelocityTtlEqualToWindow() {
        var properties = new FraudGuardProperties.VelocityRuleProperties(
                true, 300, 5, 40, "VELOCITY_EXCEEDED", 300);

        assertThat(properties.ttlSeconds()).isEqualTo(properties.windowSeconds());
    }

    @Test
    @DisplayName("Application startup must succeed with valid amount spike properties")
    void shouldStartWhenAmountSpikePropertiesAreValid() {
        amountSpikeContextRunner
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("Application startup must fail when amount spike multiplier is not greater than 1")
    void shouldFailStartupWhenAmountSpikeMultiplierIsInvalid() {
        amountSpikeContextRunner
                .withPropertyValues("fraudguard.rules.amount-spike.multiplier=1.0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must fail when minimum history exceeds history size")
    void shouldFailStartupWhenAmountSpikeMinimumHistoryExceedsHistorySize() {
        amountSpikeContextRunner
                .withPropertyValues("fraudguard.rules.amount-spike.minimum-history=6")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must fail when maximum history is below history size")
    void shouldFailStartupWhenAmountSpikeMaximumHistoryIsTooSmall() {
        amountSpikeContextRunner
                .withPropertyValues("fraudguard.rules.amount-spike.max-history=4")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must fail when amount spike weight is outside 1 to 100")
    void shouldFailStartupWhenAmountSpikeWeightIsInvalid() {
        amountSpikeContextRunner
                .withPropertyValues("fraudguard.rules.amount-spike.weight=101")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must fail when amount spike reason code is blank")
    void shouldFailStartupWhenAmountSpikeReasonCodeIsBlank() {
        amountSpikeContextRunner
                .withPropertyValues("fraudguard.rules.amount-spike.reason-code= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FraudGuardProperties.class)
    static class AmountSpikePropertiesConfiguration {
    }
}
