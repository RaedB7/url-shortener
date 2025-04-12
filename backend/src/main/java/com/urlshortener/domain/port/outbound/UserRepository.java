package com.urlshortener.domain.port.outbound;

import com.urlshortener.domain.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<User> save(User user);

    Mono<User> findById(long id);

    Mono<User> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    Mono<User> setSuspended(long id, boolean suspended);

    Flux<User> findAll(int page, int size);

    Mono<Long> count();

    Mono<Long> countLinksOwnedBy(long userId);
}
