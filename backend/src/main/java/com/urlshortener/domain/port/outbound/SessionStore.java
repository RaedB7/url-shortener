package com.urlshortener.domain.port.outbound;

import reactor.core.publisher.Mono;

public interface SessionStore {

    Mono<String> create(long userId);

    Mono<Long> userId(String token);

    Mono<Void> delete(String token);
}
