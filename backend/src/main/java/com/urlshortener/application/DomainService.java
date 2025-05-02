package com.urlshortener.application;

import com.urlshortener.domain.exception.DomainAlreadyExistsException;
import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.InvalidUrlException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.CustomDomain;
import com.urlshortener.domain.port.inbound.ManageDomainsUseCase;
import com.urlshortener.domain.port.outbound.AuditLog;
import com.urlshortener.domain.port.outbound.CustomDomainRepository;
import com.urlshortener.domain.port.outbound.DnsVerifier;
import com.urlshortener.domain.port.outbound.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

public class DomainService implements ManageDomainsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DomainService.class);
    private static final String DOMAIN_PATTERN = "^(?!-)[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$";

    private final CustomDomainRepository domainRepository;
    private final IdGenerator idGenerator;
    private final DnsVerifier dns;
    private final AuditLog auditLog;
    private final SecureRandom random = new SecureRandom();

    public DomainService(CustomDomainRepository domainRepository, IdGenerator idGenerator,
            DnsVerifier dns, AuditLog auditLog) {
        this.domainRepository = domainRepository;
        this.idGenerator = idGenerator;
        this.dns = dns;
        this.auditLog = auditLog;
    }

    @Override
    public Mono<CustomDomain> register(String rawDomain, Actor actor) {
        if (!actor.admin() && !actor.authenticated()) {
            return Mono.error(new ForbiddenException("Custom domains require an account"));
        }
        String domain = rawDomain == null ? "" : rawDomain.trim().toLowerCase();
        if (!domain.matches(DOMAIN_PATTERN)) {
            return Mono.error(new InvalidUrlException("'" + domain + "' is not a valid hostname"));
        }

        byte[] tokenBytes = new byte[16];
        random.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        CustomDomain custom = new CustomDomain(idGenerator.nextId(), domain,
                actor.userId(), token, false, Instant.now());
        return domainRepository.existsByDomain(domain)
                .flatMap(exists -> exists
                        ? Mono.error(new DomainAlreadyExistsException(domain))
                        : domainRepository.save(custom)
                                .onErrorMap(DuplicateKeyException.class,
                                        e -> new DomainAlreadyExistsException(domain))
                                .doOnNext(saved -> log.info("Registered domain {} for owner {}",
                                        domain, actor.userId())));
    }

    @Override
    public Mono<CustomDomain> verify(long domainId, Actor actor) {
        return ownedDomain(domainId, actor)
                .flatMap(domain -> dns.hasTxtRecord(domain.getDomain(), domain.getVerifyToken())
                        .flatMap(verified -> {
                            if (!verified) {
                                return Mono.error(new InvalidUrlException(
                                        "DNS TXT record not found. Add 'urlshortener-verify="
                                                + domain.getVerifyToken() + "' to " + domain.getDomain()));
                            }
                            return domainRepository.setVerified(domainId, true);
                        }));
    }

    @Override
    public Mono<CustomDomain> adminApprove(long domainId, Actor actor) {
        if (!actor.admin()) {
            return Mono.error(new ForbiddenException("Admin access required"));
        }
        return domainRepository.findById(domainId)
                .switchIfEmpty(Mono.error(new UrlNotFoundException("domain " + domainId)))
                .flatMap(domain -> domainRepository.setVerified(domainId, true)
                        .flatMap(approved -> auditLog
                                .append(actor.userId(), null, "DOMAIN_APPROVED",
                                        domain.getDomain(), null)
                                .onErrorResume(ex -> Mono.empty())
                                .thenReturn(approved)));
    }

    @Override
    public Flux<CustomDomain> myDomains(Actor actor) {
        if (!actor.authenticated()) {
            return Flux.error(new ForbiddenException("Login required to list your domains"));
        }
        return domainRepository.findAllByOwner(actor.userId());
    }

    @Override
    public Flux<CustomDomain> allDomains(Actor actor) {
        if (!actor.admin()) {
            return Flux.error(new ForbiddenException("Admin access required"));
        }
        return domainRepository.findAll();
    }

    private Mono<CustomDomain> ownedDomain(long domainId, Actor actor) {
        return domainRepository.findById(domainId)
                .switchIfEmpty(Mono.error(new UrlNotFoundException("domain " + domainId)))
                .flatMap(domain -> {
                    if (!actor.admin() && !domain.isOwnedBy(actor.userId())) {
                        return Mono.error(new ForbiddenException("You can only manage your own domains"));
                    }
                    return Mono.just(domain);
                });
    }
}