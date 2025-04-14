package com.urlshortener.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import com.urlshortener.domain.exception.AliasAlreadyExistsException;
import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase;
import com.urlshortener.domain.port.outbound.IdGenerator;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.domain.port.outbound.UrlRepository;
import com.urlshortener.domain.service.UrlRules;
import com.urlshortener.domain.service.UrlShortener;

import reactor.core.publisher.Mono;

public class CreateShortUrlService implements CreateShortUrlUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateShortUrlService.class);

    private final UrlRepository urlRepository;
    private final IdGenerator idGenerator;
    private final UrlShortener shortener;
    private final PasswordHasher passwordHasher;
    private final HostPolicy hostPolicy;

    public CreateShortUrlService(UrlRepository urlRepository, IdGenerator idGenerator,
            UrlShortener shortener, PasswordHasher passwordHasher,
            HostPolicy hostPolicy) {
        this.urlRepository = urlRepository;
        this.idGenerator = idGenerator;
        this.shortener = shortener;
        this.passwordHasher = passwordHasher;
        this.hostPolicy = hostPolicy;
    }

    public interface HostPolicy {
        Mono<OriginalUrl> assertPublic(OriginalUrl url);
    }

    @Override
    public Mono<Url> create(CreateUrlCommand command, Actor actor) {
        OriginalUrl originalUrl = new OriginalUrl(command.originalUrl());
        boolean hasAlias = command.customAlias() != null && !command.customAlias().isBlank();
        boolean hasPassword = command.password() != null && !command.password().isBlank();

        if (!actor.authenticated()) {
            UrlRules.assertGuestCreateAllowed(command.expiresAt());
        }
        if (hasAlias) {
            UrlRules.assertAliasAllowed(actor.authenticated(), command.customAlias());
        }
        if (hasPassword) {
            UrlRules.assertPasswordAllowed(actor.authenticated(), command.password());
        }

        return hostPolicy.assertPublic(originalUrl)
                .flatMap(ok -> {
                    String passwordHash = hasPassword
                            ? passwordHasher.hash(command.password())
                            : null;
                    Long ownerId = actor.authenticated() ? actor.userId() : null;
                    if (hasAlias) {
                        return createWithAlias(command.customAlias().trim(), originalUrl,
                                passwordHash, ownerId, command.expiresAt());
                    }
                    return createGenerated(originalUrl, passwordHash, ownerId, command.expiresAt());
                });
    }

    private Mono<Url> createGenerated(OriginalUrl originalUrl, String passwordHash,
            Long ownerId, Instant expiresAt) {
        long id = idGenerator.nextId();
        ShortCode code = new ShortCode(shortener.encode(id));
        Url url = new Url(id, code, originalUrl, ownerId, passwordHash, expiresAt, null, Instant.now());
        return urlRepository.save(url)
                .doOnSuccess(saved -> log.info("Created link {} for owner {}", code, ownerId));
    }

    private Mono<Url> createWithAlias(String alias, OriginalUrl originalUrl, String passwordHash,
            Long ownerId, Instant expiresAt) {
        ShortCode shortCode = new ShortCode(alias);
        return urlRepository.existsByCode(alias)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new AliasAlreadyExistsException(alias));
                    }
                    long id = idGenerator.nextId();
                    Url url = new Url(id, shortCode, originalUrl, ownerId, passwordHash,
                            expiresAt, null, Instant.now());
                    return urlRepository.save(url)
                            .onErrorMap(DuplicateKeyException.class,
                                    e -> new AliasAlreadyExistsException(alias))
                            .doOnSuccess(saved -> log.info("Created aliased link {} for owner {}",
                                    alias, ownerId));
                });
    }
}
