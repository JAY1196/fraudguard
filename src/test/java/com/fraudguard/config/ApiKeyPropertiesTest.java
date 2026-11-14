package com.fraudguard.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ApiKeyPropertiesConfiguration.class);

    @Test
    @DisplayName("Application startup must fail when API key is missing")
    void shouldFailStartupWhenApiKeyIsMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must fail when API key is shorter than 32 characters")
    void shouldFailStartupWhenApiKeyIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "fraudguard.security.api-key=short-test-key"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Application startup must succeed with a valid API key")
    void shouldStartWhenApiKeyIsValid() {
        String validApiKey = "test-only-fraudguard-api-key-2026";

        contextRunner
                .withPropertyValues(
                        "fraudguard.security.api-key=" + validApiKey
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApiKeyProperties.class);
                    assertThat(context.getBean(ApiKeyProperties.class).apiKey())
                            .isEqualTo(validApiKey);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiKeyProperties.class)
    static class ApiKeyPropertiesConfiguration {
    }
}
