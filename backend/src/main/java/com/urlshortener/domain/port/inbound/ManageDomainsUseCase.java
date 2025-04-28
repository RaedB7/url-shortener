package com.urlshortener.domain.port.inbound;

import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.CustomDomain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ManageDomainsUseCase {

    Mono<CustomDomain> register(String domain, Actor actor);

    Mono<CustomDomain> verify(long domainId, Actor actor);

    Mono<CustomDomain> adminApprove(long domainId, Actor actor);

    Flux<CustomDomain> myDomains(Actor actor);

    Flux<CustomDomain> allDomains(Actor actor);
}