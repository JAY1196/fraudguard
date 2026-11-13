
package com.fraudguard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fraudguard.security")
public record ApiKeyProperties(
        @NotBlank(message = "FRAUDGUARD_API_KEY must be configured")
        @Size(min = 32, message = "FRAUDGUARD_API_KEY must contain at least 32 characters")
        String apiKey
) {
}
