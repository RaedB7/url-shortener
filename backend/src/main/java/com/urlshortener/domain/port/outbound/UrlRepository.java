package com.urlshortener.domain.port.outbound;

import com.urlshortener.domain.model.Url;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface UrlRepository {

    Mono<Url> save(Url url);

    Mono<Url> findByCode(String code);

    Mono<Boolean> existsByCode(String code);

    Mono<Void> softDeleteByCode(String code);

    Mono<Url> updateMeta(String code, Instant expiresAt, String passwordHash, boolean removePassword);

    Mono<Page<Url>> findAllByOwner(Long ownerId, int page, int size, String search);

    Mono<Page<Url>> findAll(int page, int size, String search);

    Flux<String> findAllLiveCodes();

    record Page<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
