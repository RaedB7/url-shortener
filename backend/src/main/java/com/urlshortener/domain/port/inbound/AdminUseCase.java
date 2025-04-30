package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AdminUseCase {

    Mono<Overview> overview();

    Flux<UserWithStats> users(int page, int size);

    Mono<Long> userCount();

    Mono<User> setSuspended(long userId, boolean suspended, Actor actor);

    Mono<Void> deleteLink(String code, Actor actor);

    record Actor(long userId, String email) {}

    record Overview(long totalUsers, long totalLinks, long totalClicks,
                    String topUrl, long topUrlClicks,
                    double rateLimitDenies, double ssrfBlocks) {}

    record UserWithStats(long id, String email, String role, boolean suspended,
                         java.time.Instant createdAt, long linkCount) {}
}