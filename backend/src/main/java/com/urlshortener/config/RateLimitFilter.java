package com.urlshortener.config;

import com.urlshortener.infrastructure.ratelimit.RedisRateLimiter;
import com.urlshortener.infrastructure.util.ClientIp;
import com.urlshortener.infrastructure.web.CurrentUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@Order(-3)
public class RateLimitFilter implements WebFilter {

    private final RedisRateLimiter rateLimiter;
    private final AppProperties properties;
    private final CurrentUser currentUser;
    private final Counter denies;

    public RateLimitFilter(RedisRateLimiter rateLimiter, AppProperties properties,
            CurrentUser currentUser, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.currentUser = currentUser;
        this.denies = Counter.builder("urlshortener_ratelimit_denies_total")
                .description("Requests rejected by the rate limiter")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();
        String clientIp = ClientIp.resolve(request);
        var limits = properties.getRateLimit();

        if (method == HttpMethod.POST && (path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login"))) {
            return fixedWindow(exchange, chain, "rl:auth:" + clientIp, limits.getLoginPerMinute());
        }
        if (method == HttpMethod.POST && path.equals("/api/v1/links")) {
            return createLimits(exchange, chain, clientIp, limits);
        }
        if (method == HttpMethod.GET && path.startsWith("/api/v1/links") && path.endsWith("/stats")) {
            return fixedWindow(exchange, chain, "rl:stats:" + clientIp, limits.getStatsPerMinute());
        }
        if (method == HttpMethod.GET && !path.startsWith("/api/") && !path.startsWith("/actuator/")
                && path.length() > 1 && path.indexOf('/', 1) == -1) {
            return tokenBucket(exchange, chain, "rl:redirect:" + clientIp,
                    limits.getRedirectBurst(), limits.getRedirectRefillPerMinute());
        }
        return chain.filter(exchange);
    }

    private Mono<Void> createLimits(ServerWebExchange exchange, WebFilterChain chain,
            String clientIp, AppProperties.RateLimit limits) {
        return currentUser.actor(exchange.getRequest())
                .flatMap(actor -> {
                    if (actor.authenticated()) {
                        return fixedWindow(exchange, chain, "rl:create:" + clientIp,
                                limits.getCreateAuthPerMinute());
                    }
                    return rateLimiter.allowFixedWindow("rl:create-anon:" + clientIp,
                            limits.getCreateAnonPerMinute())
                            .flatMap(result -> {
                                if (!result.allowed()) {
                                    return deny(exchange, 60);
                                }
                                String dayKey = LocalDate.now(ZoneOffset.UTC).toString();
                                return rateLimiter.allowFixedWindow(
                                        "rl:daily:" + clientIp + ":" + dayKey, limits.getGuestDailyCap())
                                        .flatMap(daily -> daily.allowed()
                                                ? chain.filter(exchange)
                                                : deny(exchange, 3600));
                            });
                });
    }

    private Mono<Void> fixedWindow(ServerWebExchange exchange, WebFilterChain chain, String key, int max) {
        return rateLimiter.allowFixedWindow(key, max)
                .flatMap(result -> result.allowed() ? chain.filter(exchange) : deny(exchange, 60));
    }

    private Mono<Void> tokenBucket(ServerWebExchange exchange, WebFilterChain chain,
            String key, int capacity, int refill) {
        return rateLimiter.allowTokenBucket(key, capacity, refill)
                .flatMap(result -> {
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
                    return result.allowed() ? chain.filter(exchange) : deny(exchange, 60);
                });
    }

    private Mono<Void> deny(ServerWebExchange exchange, long retryAfterSeconds) {
        denies.increment();
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(retryAfterSeconds));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"title\":\"Rate Limit Exceeded\",\"status\":429,"
                + "\"detail\":\"Too many requests. Try again in " + retryAfterSeconds + " seconds.\","
                + "\"retryAfterSeconds\":" + retryAfterSeconds + "}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}