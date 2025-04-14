package com.urlshortener.config;

import com.urlshortener.domain.model.Role;
import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.outbound.IdGenerator;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.domain.port.outbound.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class AdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedConfig.class);
    private static final String ADMIN_EMAIL = "admin@demo.io";

    @Bean
    public ApplicationRunner adminSeeder(UserRepository userRepository, PasswordHasher passwordHasher,
            IdGenerator idGenerator) {
        return args -> userRepository.existsByEmail(ADMIN_EMAIL)
                .flatMap(exists -> exists
                        ? userRepository.findByEmail(ADMIN_EMAIL).flatMap(AdminSeedConfig::warnIfNotAdmin)
                        : userRepository.save(new User(idGenerator.nextId(), ADMIN_EMAIL,
                                passwordHasher.hash("admin1234"), Role.ADMIN, false, Instant.now()))
                                .doOnNext(u -> log.info("Seeded admin account {}", ADMIN_EMAIL)))
                .then()
                .block();
    }

    private static reactor.core.publisher.Mono<User> warnIfNotAdmin(User admin) {
        if (!admin.isAdmin()) {
            log.warn("{} exists but is not an ADMIN, leaving as is", ADMIN_EMAIL);
        }
        return reactor.core.publisher.Mono.just(admin);
    }
}
