package com.urlshortener.domain.port.outbound;

import com.urlshortener.domain.model.CustomDomain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomDomainRepository {

    Mono<CustomDomain> save(CustomDomain domain);

    Mono<CustomDomain> findById(long id);

    Mono<CustomDomain> findByDomain(String domain);

    Mono<Boolean> existsByDomain(String domain);

    Mono<CustomDomain> setVerified(long id, boolean verified);

    Flux<CustomDomain> findAllByOwner(long ownerId);

    Flux<CustomDomain> findAll();

    Mono<Void> deleteById(long id);
}