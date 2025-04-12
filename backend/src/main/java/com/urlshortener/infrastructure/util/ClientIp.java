package com.urlshortener.infrastructure.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

public final class ClientIp {

    public static final String UNKNOWN = "unknown";

    private ClientIp() {
    }

    public static String resolve(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return UNKNOWN;
    }
}
