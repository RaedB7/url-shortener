package com.urlshortener.infrastructure.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final RedisScript<List> TOKEN_BUCKET = RedisScript.of(
            """
                    local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
                    local tokens = tonumber(state[1])
                    local ts = tonumber(state[2])
                    if tokens == nil then
                        tokens = tonumber(ARGV[1])
                        ts = tonumber(ARGV[3])
                    end
                    local elapsed = math.max(0, ARGV[3] - ts)
                    tokens = math.min(tonumber(ARGV[1]), tokens + elapsed * tonumber(ARGV[2]) / 60000)
                    local allowed = 0
                    if tokens >= 1 then
                        tokens = tokens - 1
                        allowed = 1
                    end
                    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', ARGV[3])
                    redis.call('PEXPIRE', KEYS[1], 3600000)
                    return {allowed, math.floor(tokens)}
                    """,
            List.class);

    private final ReactiveRedisTemplate<String, String> redis;

    public RedisRateLimiter(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @SuppressWarnings("unchecked")
    public Mono<TokenBucketResult> allowTokenBucket(String key, int capacity, int refillPerMinute) {
        return redis.execute(TOKEN_BUCKET, List.of(key),
                List.of(String.valueOf(capacity), String.valueOf(refillPerMinute),
                        String.valueOf(System.currentTimeMillis())))
                .next()
                .map(result -> {
                    List<Number> values = (List<Number>) result;
                    return new TokenBucketResult(values.get(0).intValue() == 1, values.get(1).longValue());
                });
    }

    public Mono<FixedWindowResult> allowFixedWindow(String key, int maxPerMinute) {
        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redis.expire(key, Duration.ofSeconds(60))
                                .map(expired -> new FixedWindowResult(true, maxPerMinute - 1));
                    }
                    return Mono.just(new FixedWindowResult(count <= maxPerMinute, Math.max(0, maxPerMinute - count)));
                });
    }

    public record TokenBucketResult(boolean allowed, long remaining) {
    }

    public record FixedWindowResult(boolean allowed, long remaining) {
    }
}