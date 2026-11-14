package com.fraudguard.security;

import com.fraudguard.config.ApiKeyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyProperties properties;

    public ApiKeyAuthenticationFilter(ApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String configuredKey = properties.apiKey();
        String suppliedKey = request.getHeader(API_KEY_HEADER);

        if (suppliedKey != null && !suppliedKey.isBlank()
                && isValidApiKey(configuredKey, suppliedKey)) {

            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    "api-key-client",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API"))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidApiKey(String configuredKey, String suppliedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] configuredHash = digest.digest(
                    configuredKey.getBytes(StandardCharsets.UTF_8)
            );

            byte[] suppliedHash = digest.digest(
                    suppliedKey.getBytes(StandardCharsets.UTF_8)
            );

            return MessageDigest.isEqual(configuredHash, suppliedHash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
