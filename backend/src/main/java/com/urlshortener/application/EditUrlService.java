package com.urlshortener.application;

import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.EditUrlUseCase;
import com.urlshortener.domain.port.outbound.CacheRepository;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.domain.port.outbound.UrlRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class EditUrlService implements EditUrlUseCase {

    private final UrlRepository urlRepository;
    private final CacheRepository cacheRepository;
    private final PasswordHasher passwordHasher;

    public EditUrlService(UrlRepository urlRepository, CacheRepository cacheRepository,
            PasswordHasher passwordHasher) {
        this.urlRepository = urlRepository;
        this.cacheRepository = cacheRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Mono<Url> edit(String code, EditCommand command, Actor actor) {
        if (!actor.authenticated()) {
            return Mono.error(new ForbiddenException("Login required to edit links"));
        }
        return urlRepository.findByCode(code)
                .switchIfEmpty(Mono.error(new UrlNotFoundException(code)))
                .flatMap(url -> {
                    if (!actor.admin() && !url.isOwnedBy(actor.userId())) {
                        return Mono.error(new ForbiddenException("You can only edit your own links"));
                    }
                    Instant expiresAt = command.expiresAt();
                    String passwordHash = null;
                    if (command.password() != null && !command.password().isBlank()) {
                        passwordHash = passwordHasher.hash(command.password());
                    }
                    return urlRepository.updateMeta(code, expiresAt, passwordHash, command.removePassword())
                            .flatMap(updated -> cacheRepository.evict(code).thenReturn(updated));
                });
    }
}