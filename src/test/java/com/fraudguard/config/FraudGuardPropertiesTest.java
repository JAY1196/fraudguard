package com.fraudguard.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudGuardPropertiesTest {

    @Test
    @DisplayName("Default properties must use 40 review, 70 block, and Asia/Kolkata timezone")
    void shouldHaveSensibleDefaults() {
        FraudGuardProperties properties = new FraudGuardProperties(null, null);

        assertThat(properties.thresholds().review()).isEqualTo(40);
        assertThat(properties.thresholds().block()).isEqualTo(70);
        assertThat(properties.rules().unusualHours().timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(properties.rules().unusualHours().startHour()).isEqualTo(23);
        assertThat(properties.rules().unusualHours().endHour()).isEqualTo(5);
        assertThat(properties.rules().highAmount().weight()).isEqualTo(50);
        assertThat(properties.rules().unusualHours().weight()).isEqualTo(40);
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
                true, invalidHour, 5, 2_000_000L, 40, "CODE", "Asia/Kolkata", 300L, 86400L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start hour must be between 0 and 23");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 24, 25})
    @DisplayName("Hour validation: endHour must be between 0 and 23")
    void shouldFailWhenEndHourIsInvalid(int invalidHour) {
        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, 23, invalidHour, 2_000_000L, 40, "CODE", "Asia/Kolkata", 300L, 86400L
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
                true, 23, 5, 2_000_000L, invalidWeight, "CODE", "Asia/Kolkata", 300L, 86400L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight must be between 1 and 100");
    }

    @Test
    @DisplayName("Timezone validation: invalid time zone string must fail fast")
    void shouldFailWhenTimezoneIsInvalid() {
        assertThatThrownBy(() -> new FraudGuardProperties.UnusualHoursRuleProperties(
                true, 23, 5, 2_000_000L, 40, "CODE", "Invalid/Fake_Zone", 300L, 86400L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timeZone: 'Invalid/Fake_Zone'");
    }
}
