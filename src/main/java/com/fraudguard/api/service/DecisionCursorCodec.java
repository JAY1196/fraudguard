package com.fraudguard.api.service;

import com.fraudguard.exception.InvalidRequestParameterException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class DecisionCursorCodec {

    public String encode(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Decision ID must be positive");
        }

        String value = "v1:" + id;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public Long decode(String cursor) {
        if (cursor == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String value = new String(decoded, StandardCharsets.UTF_8);

            if (!value.startsWith("v1:")) {
                throw new InvalidRequestParameterException(
                        "Unsupported decision cursor version"
                );
            }

            String idValue = value.substring(3);

            if (!idValue.matches("[1-9][0-9]*")) {
                throw new InvalidRequestParameterException(
                        "Invalid decision cursor"
                );
            }

            long id = Long.parseLong(idValue);

            String canonical = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(decoded);

            if (!canonical.equals(cursor)) {
                throw new InvalidRequestParameterException(
                        "Invalid decision cursor encoding"
                );
            }

            return id;

        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestParameterException(
                    "Invalid decision cursor"
            );
        }
    }
}