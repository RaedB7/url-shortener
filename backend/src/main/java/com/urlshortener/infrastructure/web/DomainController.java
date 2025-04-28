package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.CustomDomain;
import com.urlshortener.domain.port.inbound.ManageDomainsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {

    private final ManageDomainsUseCase domainsUseCase;
    private final CurrentUser currentUser;

    public DomainController(ManageDomainsUseCase domainsUseCase, CurrentUser currentUser) {
        this.domainsUseCase = domainsUseCase;
        this.currentUser = currentUser;
    }

    public record RegisterDomainRequest(String domain) {}

    public record DomainResponse(long id, String domain, String verifyToken,
                                 boolean verified, String txtRecord) {
        public static DomainResponse from(CustomDomain d) {
            return new DomainResponse(d.getId(), d.getDomain(), d.getVerifyToken(),
                    d.isVerified(), "urlshortener-verify=" + d.getVerifyToken());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DomainResponse> register(@RequestBody RegisterDomainRequest request,
                                        ServerHttpRequest httpRequest) {
        return currentUser.actor(httpRequest)
                .flatMap(actor -> domainsUseCase.register(request.domain(), actor))
                .map(DomainResponse::from);
    }

    @GetMapping
    public Mono<List<DomainResponse>> myDomains(ServerHttpRequest request) {
        return currentUser.actor(request)
                .flatMapMany(actor -> domainsUseCase.myDomains(actor))
                .map(DomainResponse::from)
                .collectList();
    }

    @PostMapping("/{id}/verify")
    public Mono<DomainResponse> verify(@PathVariable long id, ServerHttpRequest request) {
        return currentUser.actor(request)
                .flatMap(actor -> domainsUseCase.verify(id, actor))
                .map(DomainResponse::from);
    }

    @GetMapping("/all")
    public Mono<List<DomainResponse>> allDomains(ServerHttpRequest request) {
        return currentUser.actor(request)
                .flatMapMany(actor -> domainsUseCase.allDomains(actor))
                .map(DomainResponse::from)
                .collectList();
    }

    @PostMapping("/{id}/approve")
    public Mono<DomainResponse> approve(@PathVariable long id, ServerHttpRequest request) {
        return currentUser.actor(request)
                .flatMap(actor -> domainsUseCase.adminApprove(id, actor))
                .map(DomainResponse::from);
    }
}