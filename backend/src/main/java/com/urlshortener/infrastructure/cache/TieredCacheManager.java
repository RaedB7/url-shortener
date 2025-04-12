package com.urlshortener.infrastructure.cache;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.CacheRepository;
import com.urlshortener.domain.port.outbound.UrlRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

@Component
@Primary
public class TieredCacheManager implements CacheRepository {

    private static final Logger log = LoggerFactory.getLogger(TieredCacheManager.class);
    private static final Duration L1_TTL = Duration.ofMinutes(10);
    private static final Duration L2_TTL = Duration.ofHours(1);

    private final CaffeineCacheAdapter l1;
    private final RedisCacheAdapter l2;
    private final UrlRepository urlRepository;
    private final BloomFilterManager bloomFilter;
    private final Timer cacheGetTimer;

    public TieredCacheManager(CaffeineCacheAdapter l1, RedisCacheAdapter l2,
            UrlRepository urlRepository, BloomFilterManager bloomFilter,
            MeterRegistry meterRegistry) {
        this.l1 = l1;
        this.l2 = l2;
        this.urlRepository = urlRepository;
        this.bloomFilter = bloomFilter;
        this.cacheGetTimer = Timer.builder("urlshortener_cache_get")
                .description("Cache lookup latency across tiers")
                .tag("result", "hit")
                .register(meterRegistry);
    }

    @Override
    public Mono<Url> get(String code) {
        if (bloomFilter.isInitialized() && !bloomFilter.mightContain(code)) {
            return Mono.empty();
        }

        return l1.get(code)
                .switchIfEmpty(
                        l2.get(code)
                                .flatMap(url -> populateL1(code, url).thenReturn(url))
                                .switchIfEmpty(
                                        urlRepository.findByCode(code)
                                                .flatMap(url -> populateCaches(code, url).thenReturn(url))));
    }

    private Mono<Void> populateCaches(String code, Url url) {
        return l2.put(code, url, L2_TTL)
                .onErrorResume(ex -> {
                    log.warn("L2 backfill failed for {}: {}", code, ex.getMessage());
                    return Mono.empty();
                })
                .then(populateL1(code, url));
    }

    private Mono<Void> populateL1(String code, Url url) {
        return l1.put(code, url, L1_TTL)
                .onErrorResume(ex -> {
                    log.warn("L1 backfill failed for {}: {}", code, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> put(String code, Url url, Duration ttl) {
        return l2.put(code, url, L2_TTL)
                .then(l1.put(code, url, L1_TTL));
    }

    @Override
    public Mono<Void> evict(String code) {
        return l2.evict(code).then(l1.evict(code));
    }

    public double l1HitRate() {
        return l1.hitRate();
    }

    public long l1Size() {
        return l1.size();
    }

    public long bloomApproximateSize() {
        return bloomFilter.approximateSize();
    }
}
