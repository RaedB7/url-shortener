package com.urlshortener.domain.port.outbound;

import reactor.core.publisher.Mono;

public interface DnsVerifier {

    Mono<Boolean> hasTxtRecord(String domain, String expectedToken);
}