package com.urlshortener.domain.port.outbound;

import reactor.core.publisher.Mono;

public interface UniqueVisitorCounter {

    Mono<Void> record(String code, String dayKey, String ipHash);

    Mono<Long> uniquesTotal(String code);

    Mono<Long> uniquesForDay(String code, String dayKey);
}
