package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.User;
import reactor.core.publisher.Mono;

public interface AuthUseCase {

    Mono<Session> register(String email, String password);

    Mono<Session> login(String email, String password);

    Mono<User> byId(long userId);

    record Session(String token, User user) {
    }
}
