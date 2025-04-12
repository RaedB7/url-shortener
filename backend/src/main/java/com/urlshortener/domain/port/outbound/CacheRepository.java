package com.urlshortener.domain.port.outbound;

import com.urlshortener.domain.model.Url;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface CacheRepository {

    Mono<Url> get(String code);

    Mono<Void> put(String code, Url url, Duration ttl);

    Mono<Void> evict(String code);
}
