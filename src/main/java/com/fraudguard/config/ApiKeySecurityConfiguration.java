package com.fraudguard.config;

import com.fraudguard.security.ApiKeyAuthenticationEntryPoint;
import com.fraudguard.security.ApiKeyAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeySecurityConfiguration {

    @Bean
    ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
            ApiKeyProperties properties
    ) {
        return new ApiKeyAuthenticationFilter(properties);
    }

    @Bean
    ApiKeyAuthenticationEntryPoint apiKeyAuthenticationEntryPoint() {
        return new ApiKeyAuthenticationEntryPoint();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            ApiKeyAuthenticationEntryPoint authenticationEntryPoint,
            Environment environment
    ) throws Exception {

        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/actuator/health",
                            "/actuator/health/**"
                    ).permitAll();

                    if (devProfile) {
                        authorize.requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }

                    authorize.anyRequest().authenticated();
                })
                .addFilterBefore(
                        apiKeyAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration(
            ApiKeyAuthenticationFilter filter
    ) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
