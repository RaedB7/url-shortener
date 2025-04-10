package com.urlshortener.domain.model;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import com.urlshortener.domain.exception.InvalidUrlException;

public class OriginalUrl {

    private static final int MAX_LENGTH = 2048;
    private static final Set<String> BLOCKED_SCHEMES = Set.of(
            "javascript", "data", "file", "vbscript", "about");

    private final String value;

    public OriginalUrl(String raw) {
        Objects.requireNonNull(raw, "URL must not be null");

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUrlException("URL must not be empty");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("URL exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        if (!trimmed.contains("://")) {
            trimmed = "https://" + trimmed;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("URL is malformed: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidUrlException("URL must have a protocol (http or https)");
        }
        if (BLOCKED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("Protocol '" + scheme + "' is not allowed");
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new InvalidUrlException("Only http and https protocols are supported");
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidUrlException("URL must have a valid hostname");
        }
        if (isDiscouragedIpLiteral(host)) {
            throw new InvalidUrlException("URL points to a private or internal address, which is not allowed");
        }

        this.value = trimmed;
    }

    static boolean isDiscouragedIpLiteral(String host) {
        String h = host.toLowerCase();
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.startsWith("::") || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) {
            return true;
        }
        if (h.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            String[] octets = h.split("\\.");
            int[] parts = new int[4];
            for (int i = 0; i < 4; i++) {
                try {
                    parts[i] = Integer.parseInt(octets[i]);
                } catch (NumberFormatException e) {
                    return true;
                }
            }
            return isPrivateIpv4(parts);
        }
        return false;
    }

    private static boolean isPrivateIpv4(int[] o) {
        return o[0] == 10
                || o[0] == 127
                || o[0] == 0
                || (o[0] == 169 && o[1] == 254)
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168)
                || (o[0] == 100 && o[1] >= 64 && o[1] <= 127)
                || (o[0] == 198 && (o[1] == 18 || o[1] == 19));
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof OriginalUrl that))
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
