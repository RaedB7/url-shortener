package com.urlshortener.application;

import com.urlshortener.domain.exception.UrlExpiredException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.ClickEvent;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.urlshortener.domain.port.outbound.CacheRepository;
import com.urlshortener.domain.port.outbound.ClickEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public class ResolveUrlService implements ResolveUrlUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResolveUrlService.class);

    private final CacheRepository cacheRepository;
    private final ClickEventPublisher clickEventPublisher;

    public ResolveUrlService(CacheRepository cacheRepository, ClickEventPublisher clickEventPublisher) {
        this.cacheRepository = cacheRepository;
        this.clickEventPublisher = clickEventPublisher;
    }

    @Override
    public Mono<Url> resolve(String code, ClickMetadata metadata, boolean trackClick) {
        return cacheRepository.get(code)
                .switchIfEmpty(Mono.error(new UrlNotFoundException(code)))
                .flatMap(url -> {
                    if (url.isDeleted()) {
                        throw new UrlNotFoundException(code);
                    }
                    if (url.isExpired()) {
                        return cacheRepository.evict(code)
                                .then(Mono.error(new UrlExpiredException(code)));
                    }
                    if (!trackClick) {
                        return Mono.just(url);
                    }
                    return publishClickEvent(url, metadata)
                            .thenReturn(url);
                });
    }

    private Mono<Void> publishClickEvent(Url url, ClickMetadata metadata) {
        ClickEvent event = new ClickEvent(
                url.getCode().getValue(),
                Instant.now(),
                sanitizeReferrer(metadata.referrer()),
                sha256(metadata.clientIp()),
                null, null, null);
        return clickEventPublisher.publish(event, metadata.userAgent())
                .onErrorResume(ex -> {
                    log.warn("Failed to publish click event for {}: {}", url.getCode(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private String sanitizeReferrer(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return referrer;
        }
        int end = referrer.indexOf('?');
        if (end < 0) {
            end = referrer.indexOf('#');
        }
        return end < 0 ? referrer : referrer.substring(0, end);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((input == null ? "unknown" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}