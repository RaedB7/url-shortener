package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.Url;

import reactor.core.publisher.Mono;

public interface ResolveUrlUseCase {

    Mono<Url> resolve(String code, ClickMetadata metadata);

    record ClickMetadata(String clientIp, String referrer, String userAgent) {
    }
}
