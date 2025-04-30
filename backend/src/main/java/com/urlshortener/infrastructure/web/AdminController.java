package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.model.Actor;
import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.inbound.AdminUseCase;
import com.urlshortener.domain.port.inbound.ManageUrlsUseCase;
import com.urlshortener.domain.port.outbound.AuditLog;
import com.urlshortener.infrastructure.web.dto.UrlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminUseCase adminUseCase;
    private final ManageUrlsUseCase manageUrlsUseCase;
    private final AuditLog auditLog;
    private final CurrentUser currentUser;

    public AdminController(AdminUseCase adminUseCase, ManageUrlsUseCase manageUrlsUseCase,
                          AuditLog auditLog, CurrentUser currentUser) {
        this.adminUseCase = adminUseCase;
        this.manageUrlsUseCase = manageUrlsUseCase;
        this.auditLog = auditLog;
        this.currentUser = currentUser;
    }

    @GetMapping("/overview")
    public Mono<AdminUseCase.Overview> overview(ServerHttpRequest request) {
        return requireAdmin(request).then(adminUseCase.overview());
    }

    @GetMapping("/users")
    public Mono<List<AdminUseCase.UserWithStats>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            ServerHttpRequest request) {
        return requireAdmin(request).thenMany(adminUseCase.users(page, size)).collectList();
    }

    @PostMapping("/users/{id}/suspend")
    public Mono<AdminUseCase.UserWithStats> suspend(@PathVariable long id,
                                                    @RequestParam(defaultValue = "true") boolean suspended,
                                                    ServerHttpRequest request) {
        return currentUser.user(request)
                .flatMap(admin -> requireAdmin(request)
                        .then(adminUseCase.setSuspended(id, suspended,
                                new AdminUseCase.Actor(admin.getId(), admin.getEmail()))))
                .map(user -> toStats(user, -1));
    }

    @GetMapping("/links")
    public Mono<ManageUrlsUseCase.PageResult<UrlResponse>> allLinks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            ServerHttpRequest request) {
        return requireAdmin(request)
                .then(currentUser.actor(request))
                .flatMap(actor -> manageUrlsUseCase.listAllLinks(actor, page, size, search))
                .map(pageResult -> new ManageUrlsUseCase.PageResult<>(
                        pageResult.content().stream()
                                .map(url -> UrlResponse.from(url, baseUrl(request)))
                                .toList(),
                        pageResult.page(), pageResult.size(),
                        pageResult.totalElements(), pageResult.totalPages()));
    }

    @DeleteMapping("/links/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteLink(@PathVariable String code, ServerHttpRequest request) {
        return currentUser.user(request)
                .flatMap(admin -> requireAdmin(request)
                        .then(adminUseCase.deleteLink(code,
                                new AdminUseCase.Actor(admin.getId(), admin.getEmail()))));
    }

    @GetMapping("/audit")
    public Mono<List<AuditLog.Entry>> audit(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            ServerHttpRequest request) {
        return requireAdmin(request).thenMany(auditLog.latest(limit, offset)).collectList();
    }

    @GetMapping("/audit/export")
    public Mono<byte[]> auditExport(ServerHttpRequest request) {
        return requireAdmin(request)
                .then(auditLog.latest(10000, 0).collectList())
                .map(entries -> {
                    StringBuilder csv = new StringBuilder("timestamp,actor_email,action,target,detail\n");
                    for (AuditLog.Entry e : entries) {
                        csv.append(csvEscape(e.createdAt().toString())).append(',')
                           .append(csvEscape(e.actorEmail())).append(',')
                           .append(csvEscape(e.action())).append(',')
                           .append(csvEscape(e.target())).append(',')
                           .append(csvEscape(e.detail())).append('\n');
                    }
                    return csv.toString().getBytes(StandardCharsets.UTF_8);
                });
    }

    private Mono<Void> requireAdmin(ServerHttpRequest request) {
        return currentUser.user(request)
                .filter(User::isAdmin)
                .switchIfEmpty(Mono.error(new com.urlshortener.domain.exception.ForbiddenException(
                        "Admin access required")))
                .then();
    }

    private AdminUseCase.UserWithStats toStats(User user, long linkCount) {
        return new AdminUseCase.UserWithStats(user.getId(), user.getEmail(),
                user.getRole().name(), user.isSuspended(), user.getCreatedAt(), linkCount);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private String baseUrl(ServerHttpRequest request) {
        var uri = request.getURI();
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
}