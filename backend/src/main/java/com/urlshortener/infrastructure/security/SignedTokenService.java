package com.urlshortener.infrastructure.security;

import com.urlshortener.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Component
public class SignedTokenService {

    private static final long TTL_SECONDS = 86400;

    private final byte[] secret;

    public SignedTokenService(AppProperties properties) {
        this.secret = properties.getSessionSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String code, String principal) {
        String payload = code + ":" + principal + ":" + (Instant.now().getEpochSecond() + TTL_SECONDS);
        return payload + ":" + hmac(payload);
    }

    public boolean verify(String token, String code, String principal) {
        if (token == null) {
            return false;
        }
        int lastColon = token.lastIndexOf(':');
        int secondToLastColon = token.lastIndexOf(':', lastColon - 1);
        if (lastColon < 0 || secondToLastColon < 0) {
            return false;
        }
        String payload = token.substring(0, lastColon);
        String signature = token.substring(lastColon + 1);
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(secondToLastColon + 1, lastColon));
        } catch (NumberFormatException e) {
            return false;
        }
        if (!payload.startsWith(code + ":" + principal + ":")) {
            return false;
        }
        return expiry > Instant.now().getEpochSecond() && constantTimeEquals(hmac(payload), signature);
    }

    public long cookieMaxAgeSeconds() {
        return TTL_SECONDS;
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}