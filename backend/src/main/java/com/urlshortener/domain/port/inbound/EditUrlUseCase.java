package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.Url;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface EditUrlUseCase {

    Mono<Url> edit(String code, EditCommand command, com.urlshortener.domain.model.Actor actor);

    record EditCommand(Instant expiresAt, String password, boolean removePassword) {
    }
}