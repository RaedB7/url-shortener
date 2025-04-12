package com.urlshortener.domain.port.inbound;

import java.time.Instant;

import com.urlshortener.domain.model.Url;

import reactor.core.publisher.Mono;

public interface CreateShortUrlUseCase {

    Mono<Url> create(CreateUrlCommand command, Actor actor);

    record CreateUrlCommand(String originalUrl, String customAlias, String password,
            Instant expiresAt) {
    }

    record Actor(Long userId, boolean authenticated) {
    }
}
