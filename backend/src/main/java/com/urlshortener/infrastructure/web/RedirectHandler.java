package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.exception.UrlExpiredException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase.ClickMetadata;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.infrastructure.dns.PublicHostVerifier;
import com.urlshortener.infrastructure.security.SignedTokenService;
import com.urlshortener.infrastructure.util.ClientIp;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class RedirectHandler implements WebFilter {

    private final ResolveUrlUseCase resolveUrlUseCase;
    private final PublicHostVerifier hostVerifier;
    private final PasswordHasher passwordHasher;
    private final SignedTokenService signedTokens;

    public RedirectHandler(ResolveUrlUseCase resolveUrlUseCase, PublicHostVerifier hostVerifier,
            PasswordHasher passwordHasher, SignedTokenService signedTokens) {
        this.resolveUrlUseCase = resolveUrlUseCase;
        this.hostVerifier = hostVerifier;
        this.passwordHasher = passwordHasher;
        this.signedTokens = signedTokens;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.length() <= 1 || path.startsWith("/api/") || path.startsWith("/actuator/")
                || path.indexOf('/', 1) != -1) {
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

        if (request.getMethod() == HttpMethod.POST) {
            return handlePasswordSubmit(exchange, code, metadata);
        }

        return resolveUrlUseCase.resolve(code, metadata, false)
                .flatMap(url -> recheckTargetHost(exchange, url, code))
                .onErrorResume(UrlNotFoundException.class,
                        ex -> errorPage(exchange, HttpStatus.NOT_FOUND, "404",
                                "This link doesn't exist."))
                .onErrorResume(UrlExpiredException.class,
                        ex -> errorPage(exchange, HttpStatus.GONE, "410",
                                "This link has expired."))
                .then();
    }

    private Mono<Void> handlePasswordSubmit(ServerWebExchange exchange, String code, ClickMetadata metadata) {
        return readForm(exchange)
                .flatMap(form -> resolveUrlUseCase.resolve(code, metadata, false)
                        .flatMap(url -> {
                            String submitted = form.getOrDefault("password", "");
                            if (!passwordHasher.matches(submitted, url.getPasswordHash())) {
                                return passwordPage(exchange, code, true);
                            }
                            String principal = principal(exchange);
                            ResponseCookie cookie = ResponseCookie.from("us_pw_" + code,
                                    signedTokens.issue(code, principal))
                                    .httpOnly(true)
                                    .sameSite("Lax")
                                    .path("/" + code)
                                    .maxAge(signedTokens.cookieMaxAgeSeconds())
                                    .build();
                            exchange.getResponse().addCookie(cookie);
                            return verifyHostAndRedirect(exchange, url, code);
                        }))
                .onErrorResume(UrlNotFoundException.class,
                        ex -> errorPage(exchange, HttpStatus.NOT_FOUND, "404",
                                "This link doesn't exist."))
                .onErrorResume(UrlExpiredException.class,
                        ex -> errorPage(exchange, HttpStatus.GONE, "410",
                                "This link has expired."))
                .then();
    }

    private Mono<Void> recheckTargetHost(ServerWebExchange exchange, Url url, String code) {
        if (!url.isPasswordProtected()) {
            return verifyHostAndRedirect(exchange, url, code);
        }
        String principal = principal(exchange);
        String cookieName = "us_pw_" + code;
        String token = exchange.getRequest().getCookies().getFirst(cookieName) != null
                ? exchange.getRequest().getCookies().getFirst(cookieName).getValue()
                : null;
        if (signedTokens.verify(token, code, principal)) {
            return verifyHostAndRedirect(exchange, url, code);
        }
        return passwordPage(exchange, code, false);
    }

    private Mono<Void> verifyHostAndRedirect(ServerWebExchange exchange, Url url, String code) {
        ClickMetadata metadata = new ClickMetadata(
                ClientIp.resolve(exchange.getRequest()),
                exchange.getRequest().getHeaders().getFirst("Referer"),
                exchange.getRequest().getHeaders().getFirst("User-Agent"));
        return trackClick(code, metadata)
                .then(Mono.defer(() -> doRedirect(exchange, url, code)));
    }

    private Mono<Void> trackClick(String code, ClickMetadata metadata) {
        return resolveUrlUseCase.resolve(code, metadata, true)
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    private Mono<Void> doRedirect(ServerWebExchange exchange, Url url, String code) {
        String host = URI.create(url.getOriginalUrl().getValue()).getHost();
        if (host == null) {
            return redirect(exchange, url);
        }
        return hostVerifier.isPublicCached(host)
                .flatMap(isPublic -> {
                    if (!isPublic) {
                        hostVerifier.block(host, code);
                        return errorPage(exchange, HttpStatus.FORBIDDEN, "403",
                                "This link has been blocked.");
                    }
                    return redirect(exchange, url);
                });
    }

    private String principal(ServerWebExchange exchange) {
        String ip = ClientIp.resolve(exchange.getRequest());
        return "ip:" + ip;
    }

    private Mono<Void> redirect(ServerWebExchange exchange, Url url) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.getOriginalUrl().getValue()));
        return response.setComplete();
    }

    private Mono<Void> passwordPage(ServerWebExchange exchange, String code, boolean wrongPassword) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        String message = wrongPassword
                ? "<p class='err'>Wrong password, try again.</p>"
                : "";
        String html = """
                <!DOCTYPE html><html lang=en><meta charset=utf-8>
                <meta name=viewport content='width=device-width,initial-scale=1'>
                <title>Password required - URLShortener</title>
                <style>
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                  background:#0B0E14;color:#E6E9F0;display:flex;align-items:center;
                  justify-content:center;min-height:100vh;margin:0}
                .card{background:#131722;border:1px solid #232A3B;border-radius:14px;
                  padding:2.5rem;width:100%%;max-width:380px;margin:1rem;text-align:center}
                .lock{font-size:2.5rem;margin:0}
                input{width:100%%;padding:0.65rem 0.85rem;border:1px solid #232A3B;
                  border-radius:8px;background:#1A2030;color:#E6E9F0;font-size:0.95rem;
                  box-sizing:border-box;margin:0.75rem 0}
                input:focus{border-color:#6366F1;outline:none}
                button{width:100%%;padding:0.65rem;border:none;border-radius:8px;
                  background:linear-gradient(135deg,#6366F1,#8B5CF6);color:#fff;
                  font-size:0.95rem;font-weight:600;cursor:pointer}
                p{color:#8A93A6;margin:0.5rem 0 0}
                .err{color:#F87171}
                </style>
                <div class=card>
                <p class=lock>&#128274;</p>
                <h1 style='font-size:1.3rem;margin:0.5rem 0'>This link is password protected</h1>
                <form method=post action='/%s'>
                <input type=password name=password placeholder='Password' required autofocus>
                <button type=submit>Continue</button>
                </form>
                %s
                </div>
                """.formatted(code, message);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
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
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private Mono<java.util.Map<String, String>> readForm(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    String body = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    java.util.Map<String, String> form = new java.util.HashMap<>();
                    for (String pair : body.split("&")) {
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                            form.put(key, value);
                        }
                    }
                    return form;
                })
                .defaultIfEmpty(java.util.Map.of());
    }
}