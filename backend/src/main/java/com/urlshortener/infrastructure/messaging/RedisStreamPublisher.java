package com.urlshortener.infrastructure.messaging;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.urlshortener.domain.model.ClickEvent;
import com.urlshortener.domain.port.outbound.ClickEventPublisher;

import reactor.core.publisher.Mono;

@Component
public class RedisStreamPublisher implements ClickEventPublisher {

    private static final String STREAM_KEY = "clicks";

    private final ReactiveRedisTemplate<String, String> redis;

    public RedisStreamPublisher(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> publish(ClickEvent event, String userAgent) {
        Map<String, String> fields = Map.of(
                "urlCode", event.getUrlCode(),
                "clickedAt", event.getClickedAt().toString(),
                "referrer", event.getReferrer() == null ? "" : event.getReferrer(),
                "ipHash", event.getIpHash() == null ? "unknown" : event.getIpHash(),
                "userAgent", userAgent == null ? "" : userAgent);
        return redis.opsForStream().add(STREAM_KEY, fields).then();
    }
}
