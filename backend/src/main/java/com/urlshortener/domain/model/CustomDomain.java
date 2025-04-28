package com.urlshortener.domain.model;

import java.time.Instant;
import java.util.Objects;

public class CustomDomain {

    private final Long id;
    private final String domain;
    private final Long ownerId;
    private final String verifyToken;
    private final boolean verified;
    private final Instant createdAt;

    public CustomDomain(Long id, String domain, Long ownerId, String verifyToken,
                        boolean verified, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.domain = Objects.requireNonNull(domain, "domain must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.verifyToken = Objects.requireNonNull(verifyToken, "verifyToken must not be null");
        this.verified = verified;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isOwnedBy(long userId) {
        return ownerId == userId;
    }

    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public Long getOwnerId() { return ownerId; }
    public String getVerifyToken() { return verifyToken; }
    public boolean isVerified() { return verified; }
    public Instant getCreatedAt() { return createdAt; }
}