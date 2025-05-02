package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase.CreateUrlCommand;
import com.urlshortener.domain.port.inbound.EditUrlUseCase;
import com.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.urlshortener.domain.port.inbound.ManageUrlsUseCase;
import com.urlshortener.infrastructure.qr.QrCodeGenerator;
import com.urlshortener.infrastructure.util.ClientIp;
import com.urlshortener.infrastructure.web.dto.CreateUrlRequest;
import com.urlshortener.infrastructure.web.dto.UrlResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

        private final CreateShortUrlUseCase createShortUrlUseCase;
        private final ManageUrlsUseCase manageUrlsUseCase;
        private final EditUrlUseCase editUrlUseCase;
        private final GetAnalyticsUseCase analyticsUseCase;
        private final CurrentUser currentUser;
        private final QrCodeGenerator qrCodeGenerator;
        private final com.urlshortener.domain.port.outbound.CustomDomainRepository customDomainRepository;

        public LinkController(CreateShortUrlUseCase createShortUrlUseCase,
                        ManageUrlsUseCase manageUrlsUseCase,
                        EditUrlUseCase editUrlUseCase,
                        GetAnalyticsUseCase analyticsUseCase,
                        CurrentUser currentUser,
                        QrCodeGenerator qrCodeGenerator,
                        com.urlshortener.domain.port.outbound.CustomDomainRepository customDomainRepository) {
                this.createShortUrlUseCase = createShortUrlUseCase;
                this.manageUrlsUseCase = manageUrlsUseCase;
                this.editUrlUseCase = editUrlUseCase;
                this.analyticsUseCase = analyticsUseCase;
                this.currentUser = currentUser;
                this.qrCodeGenerator = qrCodeGenerator;
                this.customDomainRepository = customDomainRepository;
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
                                .flatMap(actor -> createShortUrlUseCase.create(command, actor)
                                                .flatMap(url -> baseUrl(httpRequest, actor)
                                                                .map(base -> UrlResponse.from(url, base))));
        }

        @GetMapping
        public Mono<com.urlshortener.domain.port.inbound.ManageUrlsUseCase.PageResult<UrlResponse>> listMyLinks(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        @RequestParam(required = false) String search,
                        ServerHttpRequest request) {
                return currentUser.actor(request)
                                .flatMap(actor -> manageUrlsUseCase.listMyLinks(actor, page, size, search))
                                .map(pageResult -> toResponsePage(pageResult, baseUrl(request)));
        }

        @GetMapping("/{code}")
        public Mono<UrlResponse> getLink(@PathVariable String code, ServerHttpRequest request) {
                return currentUser.actor(request)
                                .flatMap(actor -> manageUrlsUseCase.getByCode(code, actor))
                                .map(url -> UrlResponse.from(url, baseUrl(request)));
        }

        @DeleteMapping("/{code}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> deleteLink(@PathVariable String code, ServerHttpRequest request) {
                return currentUser.actor(request)
                                .flatMap(actor -> manageUrlsUseCase.delete(code, actor));
        }

        public record EditLinkRequest(String expiresAt, String password, boolean removePassword) {
        }

        @PatchMapping("/{code}")
        public Mono<UrlResponse> editLink(@PathVariable String code,
                        @RequestBody EditLinkRequest request,
                        ServerHttpRequest httpRequest) {
                Instant expiresAt = null;
                if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
                        expiresAt = Instant.parse(request.expiresAt());
                }
                EditUrlUseCase.EditCommand command = new EditUrlUseCase.EditCommand(
                                expiresAt, request.password(), request.removePassword());

                return currentUser.actor(httpRequest)
                                .flatMap(actor -> editUrlUseCase.edit(code, command, actor)
                                                .flatMap(url -> baseUrl(httpRequest, actor)
                                                                .map(base -> UrlResponse.from(url, base))));
        }

        @GetMapping("/{code}/stats")
        public Mono<GetAnalyticsUseCase.LinkStats> getLinkStats(
                        @PathVariable String code,
                        @RequestParam(required = false) Instant from,
                        @RequestParam(required = false) Instant to,
                        ServerHttpRequest request) {
                return currentUser.actor(request)
                                .flatMap(actor -> manageUrlsUseCase.getByCode(code, actor))
                                .then(analyticsUseCase.statsFor(code, from, to));
        }

        @GetMapping(value = "/{code}/qr", produces = MediaType.IMAGE_PNG_VALUE)
        public Mono<byte[]> qrCode(@PathVariable String code, ServerHttpRequest request) {
                return currentUser.actor(request)
                                .flatMap(actor -> baseUrl(request, actor)
                                                .flatMap(base -> qrCodeGenerator.generate(base + "/" + code)));
        }

        private ManageUrlsUseCase.PageResult<UrlResponse> toResponsePage(
                        ManageUrlsUseCase.PageResult<com.urlshortener.domain.model.Url> page,
                        String baseUrl) {
                return new ManageUrlsUseCase.PageResult<>(
                                page.content().stream().map(url -> UrlResponse.from(url, baseUrl)).toList(),
                                page.page(), page.size(), page.totalElements(), page.totalPages());
        }

        private String baseUrl(ServerHttpRequest request) {
                var uri = request.getURI();
                return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        }

        private Mono<String> baseUrl(ServerHttpRequest request, com.urlshortener.domain.model.Actor actor) {
                String host = request.getHeaders().getFirst("Host");
                if (host == null || host.isBlank() || !actor.authenticated()) {
                        return Mono.just(baseUrl(request));
                }
                String cleanHost = host.split(":")[0].toLowerCase();
                return customDomainRepository.findByDomain(cleanHost)
                                .filter(d -> d.isVerified() && d.isOwnedBy(actor.userId()))
                                .map(d -> "https://" + d.getDomain())
                                .defaultIfEmpty(baseUrl(request));
        }
}
