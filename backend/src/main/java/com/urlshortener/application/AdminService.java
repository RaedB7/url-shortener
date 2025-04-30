package com.urlshortener.application;

import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.inbound.AdminUseCase;
import com.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.urlshortener.domain.port.inbound.ManageUrlsUseCase;
import com.urlshortener.domain.port.outbound.AuditLog;
import com.urlshortener.domain.port.outbound.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AdminService implements AdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final GetAnalyticsUseCase analytics;
    private final ManageUrlsUseCase manageUrls;
    private final AuditLog auditLog;
    private final MeterRegistry meterRegistry;

    public AdminService(UserRepository userRepository, GetAnalyticsUseCase analytics,
                        ManageUrlsUseCase manageUrls, AuditLog auditLog,
                        MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.analytics = analytics;
        this.manageUrls = manageUrls;
        this.auditLog = auditLog;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Overview> overview() {
        return analytics.overview()
                .map(o -> new Overview(
                        o.totalUsers(),
                        o.totalLinks(),
                        o.totalClicks(),
                        o.topUrl(),
                        o.topUrlClicks(),
                        counterValue("urlshortener_ratelimit_denies_total"),
                        counterValue("urlshortener_abuse_blocked_total")));
    }

    @Override
    public Flux<UserWithStats> users(int page, int size) {
        return userRepository.findAll(page, size)
                .flatMap(user -> userRepository.countLinksOwnedBy(user.getId())
                        .map(count -> new UserWithStats(
                                user.getId(),
                                user.getEmail(),
                                user.getRole().name(),
                                user.isSuspended(),
                                user.getCreatedAt(),
                                count)));
    }

    @Override
    public Mono<Long> userCount() {
        return userRepository.count();
    }

    @Override
    public Mono<User> setSuspended(long userId, boolean suspended, Actor actor) {
        if (userId == actor.userId()) {
            return Mono.error(new ForbiddenException("You cannot suspend your own account"));
        }
        return userRepository.setSuspended(userId, suspended)
                .flatMap(user -> auditLog
                        .append(actor.userId(), actor.email(),
                                suspended ? "USER_SUSPENDED" : "USER_UNSUSPENDED",
                                user.getEmail(), null)
                        .onErrorResume(ex -> Mono.empty())
                        .thenReturn(user)
                        .doOnNext(u -> log.info("Admin {} set user {} suspended={}",
                                actor.email(), user.getEmail(), suspended)));
    }

    @Override
    public Mono<Void> deleteLink(String code, Actor actor) {
        return manageUrls.delete(code, com.urlshortener.domain.model.Actor.admin(actor.userId()))
                .then(auditLog.append(actor.userId(), actor.email(),
                        "LINK_DELETED", code, null))
                .onErrorResume(ex -> Mono.empty());
    }

    private double counterValue(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0.0;
    }
}