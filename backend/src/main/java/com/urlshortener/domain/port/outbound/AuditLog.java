package com.urlshortener.domain.port.outbound;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuditLog {

    Mono<Void> append(Long actorId, String actorEmail, String action, String target, String detail);

    Flux<Entry> latest(int limit, int offset);

    Mono<Long> count();

    record Entry(Long actorId, String actorEmail, String action, String target,
            String detail, java.time.Instant createdAt) {
    }
}
