package com.urlshortener.domain.service;

import java.time.Duration;
import java.time.Instant;

import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.InvalidUrlException;

public class UrlRules {

    private static final Duration MAX_GUEST_EXPIRY = Duration.ofDays(7);

    public static void assertGuestCreateAllowed(Instant expiresAt) {
        if (expiresAt == null) {
            throw new InvalidUrlException("Links created without an account must have an expiry date (max 7 days)");
        }
        if (Instant.now().isAfter(expiresAt)) {
            throw new InvalidUrlException("Expiry date must be in the future");
        }
        if (Duration.between(Instant.now(), expiresAt).compareTo(MAX_GUEST_EXPIRY) > 0) {
            throw new InvalidUrlException("Guest links can expire at most 7 days from now");
        }
    }

    public static void assertAliasAllowed(boolean authenticated, String alias) {
        if (!authenticated) {
            throw new ForbiddenException("Custom aliases require an account");
        }
    }

    public static void assertPasswordAllowed(boolean authenticated, String password) {
        if (!authenticated) {
            throw new ForbiddenException("Password protection requires an account");
        }
    }
}
