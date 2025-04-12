package com.urlshortener.infrastructure.cache;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import com.urlshortener.domain.port.outbound.UniqueVisitorCounter;

import reactor.core.publisher.Mono;

public class RedisUniqueVisitorCounter implements UniqueVisitorCounter {

    private static final String TOTAL_PREFIX = "hll:";
    private static final String DAY_PREFIX = "hll:d:";
    private static final Duration DAY_TTL = Duration.ofDays(45);

    private final ReactiveRedisTemplate<String, String> redis;

    public RedisUniqueVisitorCounter(
            @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> record(String code, String dayKey, String ipHash) {
        String totalKey = TOTAL_PREFIX + code;
        String dayKeyFull = DAY_PREFIX + code + ":" + dayKey;
        return redis.opsForHyperLogLog()
                .add(totalKey, ipHash)
                .then(redis.opsForHyperLogLog().add(dayKeyFull, ipHash))
                .then(redis.expire(dayKeyFull, DAY_TTL))
                .then();
    }

    @Override
    public Mono<Long> uniquesTotal(String code) {
        return redis.opsForHyperLogLog().size(TOTAL_PREFIX + code);
    }

    @Override
    public Mono<Long> uniquesForDay(String code, String dayKey) {
        return redis.opsForHyperLogLog().size(DAY_PREFIX + code + ":" + dayKey);
    }
}
