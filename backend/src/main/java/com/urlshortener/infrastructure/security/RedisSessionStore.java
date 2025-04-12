package com.urlshortener.infrastructure.security;

import com.urlshortener.domain.port.outbound.SessionStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "session:";
    private static final Duration TTL = Duration.ofDays(7);

    private final ReactiveRedisTemplate<String, String> redis;
    private final SecureRandom random = new SecureRandom();

    public RedisSessionStore(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public Mono<String> create(long userId) {
        byte[] token = new byte[32];
        random.nextBytes(token);
        String hex = HexFormat.of().formatHex(token);
        return redis.opsForValue().set(KEY_PREFIX + hex, Long.toString(userId), TTL)
                .thenReturn(hex);
    }

    @Override
    public Mono<Long> userId(String token) {
        return redis.opsForValue().get(KEY_PREFIX + token)
                .flatMap(id -> redis.expire(KEY_PREFIX + token, TTL)
                        .thenReturn(Long.parseLong(id)));
    }

    @Override
    public Mono<Void> delete(String token) {
        return redis.opsForValue().delete(KEY_PREFIX + token).then();
    }
}
