package com.urlshortener.domain.model;

import java.util.Objects;
import java.util.Set;

import com.urlshortener.domain.exception.InvalidUrlException;

public class ShortCode {

    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "links", "login", "logout", "register", "auth",
            "dashboard", "stats", "health", "actuator", "metrics", "prometheus",
            "analytics", "overview", "qr", "static", "assets", "favicon",
            "docs", "p", "domains", "me");

    private static final int MAX_LENGTH = 20;
    private static final String PATTERN = "^[a-zA-Z0-9_-]+$";

    private final String value;

    public ShortCode(String value) {
        Objects.requireNonNull(value, "short code must not be null");

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUrlException("Short code must not be empty");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("Short code must be at most " + MAX_LENGTH + " characters");
        }
        if (!trimmed.matches(PATTERN)) {
            throw new InvalidUrlException(
                    "Short code contains invalid characters. Use letters, numbers, hyphens, and underscores only.");
        }
        if (RESERVED.contains(trimmed.toLowerCase())) {
            throw new InvalidUrlException("'" + trimmed + "' is a reserved word and cannot be used as a short code");
        }

        this.value = trimmed;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ShortCode that))
            return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
