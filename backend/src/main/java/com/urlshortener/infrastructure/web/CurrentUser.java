package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase.Actor;
import com.urlshortener.domain.port.outbound.SessionStore;
import com.urlshortener.infrastructure.util.ClientIp;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CurrentUser {

    public static final String COOKIE_NAME = "us_session";

    private final SessionStore sessionStore;
    private final UserLookup userLookup;

    public CurrentUser(SessionStore sessionStore, UserLookup userLookup) {
        this.sessionStore = sessionStore;
        this.userLookup = userLookup;
    }

    public interface UserLookup {
        Mono<User> byId(long userId);
    }

    public Mono<Actor> actor(ServerHttpRequest request) {
        return user(request).map(u -> new Actor(u.getId(), true))
                .defaultIfEmpty(new Actor(null, false));
    }

    public Mono<User> user(ServerHttpRequest request) {
        String token = token(request);
        if (token == null) {
            return Mono.empty();
        }
        return sessionStore.userId(token)
                .flatMap(userLookup::byId)
                .filter(u -> !u.isSuspended());
    }

    public String token(ServerHttpRequest request) {
        return request.getCookies().getFirst(COOKIE_NAME) != null
                ? request.getCookies().getFirst(COOKIE_NAME).getValue()
                : null;
    }

    public void setSessionCookie(ServerHttpResponse response, String token, int ttlDays) {
        response.addCookie(ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(java.time.Duration.ofDays(ttlDays))
                .build());
    }

    public void clearSessionCookie(ServerHttpResponse response) {
        response.addCookie(ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(java.time.Duration.ZERO)
                .build());
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }
}
