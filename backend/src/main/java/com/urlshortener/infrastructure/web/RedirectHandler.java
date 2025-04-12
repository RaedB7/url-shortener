package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.exception.UrlExpiredException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase.ClickMetadata;
import com.urlshortener.infrastructure.util.ClientIp;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class RedirectHandler implements WebFilter {

    private final ResolveUrlUseCase resolveUrlUseCase;

    public RedirectHandler(ResolveUrlUseCase resolveUrlUseCase) {
        this.resolveUrlUseCase = resolveUrlUseCase;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.length() <= 1 || path.startsWith("/api/") || path.startsWith("/actuator/")
                || path.equals("/") || path.contains("/")) {
            return chain.filter(exchange);
        }

        String code = path.substring(1);
        if (code.contains(".")) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        ClickMetadata metadata = new ClickMetadata(
                ClientIp.resolve(request),
                request.getHeaders().getFirst("Referer"),
                request.getHeaders().getFirst("User-Agent"));

        return resolveUrlUseCase.resolve(code, metadata)
                .flatMap(url -> redirect(exchange, url))
                .onErrorResume(UrlNotFoundException.class,
                        ex -> errorPage(exchange, HttpStatus.NOT_FOUND, "404",
                                "This link doesn't exist."))
                .onErrorResume(UrlExpiredException.class,
                        ex -> errorPage(exchange, HttpStatus.GONE, "410",
                                "This link has expired."))
                .then();
    }

    private Mono<Void> redirect(ServerWebExchange exchange, Url url) {
        // 302 not 301: a permanent redirect gets cached by browsers and keeps
        // serving stale targets for months after an edit; temporary keeps us
        // in the loop for analytics and expiry too
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.getOriginalUrl().getValue()));
        return response.setComplete();
    }

    private Mono<Void> errorPage(ServerWebExchange exchange, HttpStatus status,
            String title, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        String html = """
                <!DOCTYPE html><html lang=en><meta charset=utf-8>
                <meta name=viewport content='width=device-width,initial-scale=1'>
                <title>%s - URLShortener</title>
                <style>
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                  background:#0B0E14;color:#E6E9F0;display:flex;align-items:center;
                  justify-content:center;min-height:100vh;margin:0;text-align:center}
                h1{font-size:5rem;margin:0;background:linear-gradient(135deg,#6366F1,#8B5CF6);
                  -webkit-background-clip:text;-webkit-text-fill-color:transparent}
                p{color:#8A93A6;margin:1rem 0}
                a{color:#6366F1;text-decoration:none}
                </style>
                <div><h1>%s</h1><p>%s</p><a href='/'>Go to URLShortener</a></div>
                """.formatted(title, title, message);
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
