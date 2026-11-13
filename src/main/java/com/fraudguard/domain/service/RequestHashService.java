package com.fraudguard.domain.service;

import com.fraudguard.api.dto.EvaluateRiskRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RequestHashService {

    public String hash(EvaluateRiskRequest request) {
        String canonicalPayload = String.join("|",
                field(request.eventId().toString()),
                field(request.sourceWalletId().toString()),
                field(request.destinationWalletId().toString()),
                field(request.amountPaise().toString()),
                field(request.type().name()),
                field(normalizeCurrency(request.currency())),
                field(request.timestamp().toString())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "INR";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String field(String value) {
        return value.length() + ":" + value;
    }
}