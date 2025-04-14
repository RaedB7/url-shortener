package com.urlshortener.infrastructure.web;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.exception.UnauthorizedException;
import com.urlshortener.domain.port.inbound.AuthUseCase;
import com.urlshortener.infrastructure.web.dto.MeResponse;
import com.urlshortener.infrastructure.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

        private final AuthUseCase authUseCase;
        private final CurrentUser currentUser;
        private final AppProperties properties;

        public AuthController(AuthUseCase authUseCase, CurrentUser currentUser, AppProperties properties) {
                this.authUseCase = authUseCase;
                this.currentUser = currentUser;
                this.properties = properties;
        }

        @PostMapping("/register")
        @ResponseStatus(HttpStatus.CREATED)
        public Mono<MeResponse> register(@Valid @RequestBody RegisterRequest request,
                        ServerHttpResponse response) {
                return authUseCase.register(request.email(), request.password())
                                .doOnNext(session -> currentUser.setSessionCookie(response, session.token(),
                                                properties.getSessionTtlDays()))
                                .map(session -> new MeResponse(session.user().getEmail(),
                                                session.user().getRole().name()));
        }

        @PostMapping("/login")
        public Mono<MeResponse> login(@Valid @RequestBody RegisterRequest request,
                        ServerHttpResponse response) {
                return authUseCase.login(request.email(), request.password())
                                .doOnNext(session -> currentUser.setSessionCookie(response, session.token(),
                                                properties.getSessionTtlDays()))
                                .map(session -> new MeResponse(session.user().getEmail(),
                                                session.user().getRole().name()));
        }

        @PostMapping("/logout")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public Mono<Void> logout(ServerHttpRequest request, ServerHttpResponse response) {
                String token = currentUser.token(request);
                currentUser.clearSessionCookie(response);
                if (token == null) {
                        return Mono.empty();
                }
                return currentUser.sessionStore().delete(token);
        }

        @GetMapping("/me")
        public Mono<MeResponse> me(ServerHttpRequest request) {
                return currentUser.user(request)
                                .switchIfEmpty(reactor.core.publisher.Mono.error(
                                                new UnauthorizedException("Not logged in")))
                                .map(user -> new MeResponse(user.getEmail(), user.getRole().name()));
        }
}
