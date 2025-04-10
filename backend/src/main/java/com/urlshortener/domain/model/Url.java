package com.urlshortener.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Url {

    private final Long id;
    private final ShortCode code;
    private final OriginalUrl originalUrl;
    private final Long ownerId;
    private final String passwordHash;
    private final Instant expiresAt;
    private final Instant deletedAt;
    private final Instant createdAt;

    public Url(Long id, ShortCode code, OriginalUrl originalUrl, Long ownerId,
            String passwordHash, Instant expiresAt, Instant deletedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl must not be null");
        this.ownerId = ownerId;
        this.passwordHash = passwordHash;
        this.expiresAt = expiresAt;
        this.deletedAt = deletedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(long userId) {
        return ownerId != null && ownerId == userId;
    }

    public boolean isGuestLink() {
        return ownerId == null;
    }

    public boolean isPasswordProtected() {
        return passwordHash != null;
    }

    public Long getId() {
        return id;
    }

    public ShortCode getCode() {
        return code;
    }

    public OriginalUrl getOriginalUrl() {
        return originalUrl;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Url url))
            return false;
        return id.equals(url.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Url{code=" + code + ", owner=" + ownerId + "}";
    }
}
