package com.urlshortener.infrastructure.cache;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.CacheRepository;

import reactor.core.publisher.Mono;

@Component
public class RedisCacheAdapter implements CacheRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAdapter.class);
    private static final String KEY_PREFIX = "url:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final ReactiveRedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    RedisCacheAdapter(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Url> get(String code) {
        return redis.opsForValue().get(KEY_PREFIX + code)
                .flatMap(json -> {
                    Url url = deserialize(json);
                    if (url != null && !url.isExpired() && !url.isDeleted()) {
                        return Mono.just(url);
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> put(String code, Url url, Duration ttl) {
        String json = serialize(url);
        Duration effectiveTtl = ttl != null ? ttl : DEFAULT_TTL;
        return redis.opsForValue().set(KEY_PREFIX + code, json, effectiveTtl).then();
    }

    @Override
    public Mono<Void> evict(String code) {
        return redis.opsForValue().delete(KEY_PREFIX + code).then();
    }

    private String serialize(Url url) {
        try {
            return objectMapper.writeValueAsString(new CachedUrl(
                    url.getId(),
                    url.getCode().getValue(),
                    url.getOriginalUrl().getValue(),
                    url.getOwnerId(),
                    url.getPasswordHash(),
                    url.getExpiresAt(),
                    url.getDeletedAt(),
                    url.getCreatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cached URL", e);
        }
    }

    private Url deserialize(String json) {
        try {
            CachedUrl cached = objectMapper.readValue(json, CachedUrl.class);
            return new Url(
                    cached.id(),
                    new ShortCode(cached.code()),
                    new OriginalUrl(cached.originalUrl()),
                    cached.ownerId(),
                    cached.passwordHash(),
                    cached.expiresAt(),
                    cached.deletedAt(),
                    cached.createdAt());
        } catch (IOException e) {
            log.warn("Bad cached value, treating as miss: {}", e.getMessage());
            return null;
        }
    }

    private record CachedUrl(long id, String code, String originalUrl, Long ownerId,
            String passwordHash, Instant expiresAt, Instant deletedAt,
            Instant createdAt) {
    }
}
