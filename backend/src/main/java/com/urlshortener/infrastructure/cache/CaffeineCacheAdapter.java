package com.urlshortener.infrastructure.cache;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.CacheRepository;

import reactor.core.publisher.Mono;

@Component
public class CaffeineCacheAdapter implements CacheRepository {

    private final Cache<String, Url> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    @Override
    public Mono<Url> get(String code) {
        return Mono.justOrEmpty(cache.getIfPresent(code));
    }

    @Override
    public Mono<Void> put(String code, Url url, Duration ttl) {
        cache.put(code, url);
        return Mono.empty();
    }

    @Override
    public Mono<Void> evict(String code) {
        cache.invalidate(code);
        return Mono.empty();
    }

    public long size() {
        return cache.estimatedSize();
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }
}
