package com.urlshortener.application;

import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.ManageUrlsUseCase;
import com.urlshortener.domain.port.outbound.CacheRepository;
import com.urlshortener.domain.port.outbound.UrlRepository;
import reactor.core.publisher.Mono;

public class ManageUrlsService implements ManageUrlsUseCase {

    private final UrlRepository urlRepository;
    private final CacheRepository cacheRepository;

    public ManageUrlsService(UrlRepository urlRepository, CacheRepository cacheRepository) {
        this.urlRepository = urlRepository;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public Mono<Url> getByCode(String code, Actor actor) {
        return urlRepository.findByCode(code)
                .switchIfEmpty(Mono.error(new UrlNotFoundException(code)))
                .flatMap(url -> {
                    if (!actor.admin() && !url.isOwnedBy(actor.userId())) {
                        return Mono.error(new ForbiddenException("You can only view your own links"));
                    }
                    return Mono.just(url);
                });
    }

    @Override
    public Mono<PageResult<Url>> listMyLinks(Actor actor, int page, int size, String search) {
        if (!actor.authenticated()) {
            return Mono.error(new ForbiddenException("Login required to list your links"));
        }
        return urlRepository.findAllByOwner(actor.userId(), page, size, search)
                .map(this::toPageResult);
    }

    @Override
    public Mono<PageResult<Url>> listAllLinks(Actor actor, int page, int size, String search) {
        if (!actor.admin()) {
            return Mono.error(new ForbiddenException("Admin access required"));
        }
        return urlRepository.findAll(page, size, search).map(this::toPageResult);
    }

    @Override
    public Mono<Void> delete(String code, Actor actor) {
        return urlRepository.findByCode(code)
                .switchIfEmpty(Mono.error(new UrlNotFoundException(code)))
                .flatMap(url -> {
                    if (!actor.admin() && !url.isOwnedBy(actor.userId())) {
                        return Mono.error(new ForbiddenException("You can only delete your own links"));
                    }
                    return urlRepository.softDeleteByCode(code)
                            .then(cacheRepository.evict(code));
                });
    }

    private PageResult<Url> toPageResult(UrlRepository.Page<Url> page) {
        return new PageResult<>(page.content(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }
}
