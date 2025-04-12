package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase.Actor;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase.CreateUrlCommand;
import com.urlshortener.infrastructure.web.dto.CreateUrlRequest;
import com.urlshortener.infrastructure.web.dto.UrlResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/links")
public class UrlController {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    private final CreateShortUrlUseCase createShortUrlUseCase;
    private final CurrentUser currentUser;

    public UrlController(CreateShortUrlUseCase createShortUrlUseCase, CurrentUser currentUser) {
        this.createShortUrlUseCase = createShortUrlUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request,
            ServerHttpRequest httpRequest) {
        Instant expiresAt = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            expiresAt = Instant.parse(request.expiresAt());
        }

        CreateUrlCommand command = new CreateUrlCommand(
                request.url(), request.alias(), request.password(), expiresAt);

        return currentUser.actor(httpRequest)
                .flatMap(actor -> createShortUrlUseCase.create(command, actor))
                .map(url -> UrlResponse.from(url, baseUrl(httpRequest)))
                .doOnSuccess(resp -> log.info("Created short link {}", resp.code()));
    }

    private String baseUrl(ServerHttpRequest request) {
        var uri = request.getURI();
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
}
