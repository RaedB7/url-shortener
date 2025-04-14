package com.urlshortener.infrastructure.web;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-4)
public class SuspendedUserFilter implements WebFilter {

    private final CurrentUser currentUser;

    public SuspendedUserFilter(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var method = exchange.getRequest().getMethod();
        boolean mutation = method == HttpMethod.POST || method == HttpMethod.DELETE
                || method == HttpMethod.PUT || method == HttpMethod.PATCH;
        if (!mutation || !exchange.getRequest().getPath().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        return currentUser.user(exchange.getRequest())
                .map(u -> u.isSuspended())
                .defaultIfEmpty(false)
                .flatMap(suspended -> {
                    if (suspended) {
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }
}
