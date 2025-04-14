package com.urlshortener.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.urlshortener.domain.exception.AccountSuspendedException;
import com.urlshortener.domain.exception.EmailTakenException;
import com.urlshortener.domain.exception.UnauthorizedException;
import com.urlshortener.domain.model.Role;
import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.inbound.AuthUseCase;
import com.urlshortener.domain.port.outbound.AuditLog;
import com.urlshortener.domain.port.outbound.IdGenerator;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.domain.port.outbound.SessionStore;
import com.urlshortener.domain.port.outbound.UserRepository;

import reactor.core.publisher.Mono;

public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionStore sessionStore;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher,
            SessionStore sessionStore, IdGenerator idGenerator, AuditLog auditLog) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionStore = sessionStore;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
    }

    @Override
    public Mono<Session> register(String email, String password) {
        String normalized = email.trim().toLowerCase();
        if (password == null || password.length() < 8) {
            return Mono.error(new UnauthorizedException("Password must be at least 8 characters"));
        }
        return userRepository.existsByEmail(normalized)
                .flatMap(taken -> taken
                        ? Mono.error(new EmailTakenException(normalized))
                        : userRepository.save(new User(idGenerator.nextId(), normalized,
                                passwordHasher.hash(password), Role.USER, false, Instant.now())))
                .flatMap(user -> sessionStore.create(user.getId())
                        .flatMap(token -> auditLog
                                .append(user.getId(), user.getEmail(), "USER_REGISTERED", user.getEmail(), null)
                                .onErrorResume(ex -> Mono.empty())
                                .thenReturn(new Session(token, user))));
    }

    @Override
    public Mono<Session> login(String email, String password) {
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid email or password")))
                .flatMap(user -> {
                    if (user.isSuspended()) {
                        return Mono.error(new AccountSuspendedException("This account is suspended"));
                    }
                    if (!passwordHasher.matches(password, user.getPasswordHash())) {
                        return Mono.error(new UnauthorizedException("Invalid email or password"));
                    }
                    return sessionStore.create(user.getId())
                            .map(token -> new Session(token, user));
                });
    }

    @Override
    public Mono<User> byId(long userId) {
        return userRepository.findById(userId);
    }
}
