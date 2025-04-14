package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.Url;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface CreateShortUrlUseCase {

    Mono<Url> create(CreateUrlCommand command, Actor actor);

    record CreateUrlCommand(String originalUrl, String customAlias, String password,
            Instant expiresAt) {
    }
}
