package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.Url;

import reactor.core.publisher.Mono;

public interface ManageUrlsUseCase {

    Mono<Url> getByCode(String code, Actor actor);

    Mono<PageResult<Url>> listMyLinks(Actor actor, int page, int size, String search);

    Mono<PageResult<Url>> listAllLinks(Actor actor, int page, int size, String search);

    Mono<Void> delete(String code, Actor actor);

    record Actor(Long userId, boolean admin) {
    }

    record PageResult<T>(java.util.List<T> content, int page, int size,
            long totalElements, int totalPages) {
    }
}
