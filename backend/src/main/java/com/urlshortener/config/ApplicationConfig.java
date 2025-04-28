package com.urlshortener.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import com.urlshortener.application.AnalyticsQueryService;
import com.urlshortener.application.AuthService;
import com.urlshortener.application.CreateShortUrlService;
import com.urlshortener.application.DomainService;
import com.urlshortener.application.ManageUrlsService;
import com.urlshortener.application.ResolveUrlService;
import com.urlshortener.domain.port.inbound.AuthUseCase;
import com.urlshortener.domain.port.inbound.CreateShortUrlUseCase;
import com.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.urlshortener.domain.port.inbound.ManageDomainsUseCase;
import com.urlshortener.domain.port.inbound.ManageUrlsUseCase;
import com.urlshortener.domain.port.inbound.ResolveUrlUseCase;
import com.urlshortener.domain.port.outbound.AuditLog;
import com.urlshortener.domain.port.outbound.CacheRepository;
import com.urlshortener.domain.port.outbound.ClickEventPublisher;
import com.urlshortener.domain.port.outbound.ClickEventRepository;
import com.urlshortener.domain.port.outbound.CustomDomainRepository;
import com.urlshortener.domain.port.outbound.DnsVerifier;
import com.urlshortener.domain.port.outbound.IdGenerator;
import com.urlshortener.domain.port.outbound.PasswordHasher;
import com.urlshortener.domain.port.outbound.SessionStore;
import com.urlshortener.domain.port.outbound.UniqueVisitorCounter;
import com.urlshortener.domain.port.outbound.UrlRepository;
import com.urlshortener.domain.port.outbound.UserRepository;
import com.urlshortener.domain.service.UrlShortener;
import com.urlshortener.infrastructure.cache.RedisUniqueVisitorCounter;
import com.urlshortener.infrastructure.dns.PublicHostVerifier;
import com.urlshortener.infrastructure.idgen.SnowflakeIdGenerator;
import com.urlshortener.infrastructure.security.BcryptPasswordHasher;
import com.urlshortener.infrastructure.security.RedisSessionStore;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

    @Bean
    public UrlShortener urlShortener() {
        return new UrlShortener();
    }

    @Bean
    public IdGenerator idGenerator(AppProperties properties) {
        return new SnowflakeIdGenerator(properties.getWorkerId());
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new BcryptPasswordHasher();
    }

    @Bean
    public PublicHostVerifier publicHostVerifier(MeterRegistry meterRegistry) {
        return new PublicHostVerifier(meterRegistry);
    }

    @Bean
    public CreateShortUrlUseCase createShortUrlUseCase(UrlRepository urlRepository,
            IdGenerator idGenerator,
            UrlShortener urlShortener,
            PasswordHasher passwordHasher,
            PublicHostVerifier hostVerifier) {
        return new CreateShortUrlService(urlRepository, idGenerator, urlShortener,
                passwordHasher, hostVerifier);
    }

    @Bean
    public ResolveUrlUseCase resolveUrlUseCase(CacheRepository cacheRepository,
            ClickEventPublisher clickEventPublisher) {
        return new ResolveUrlService(cacheRepository, clickEventPublisher);
    }

    @Bean
    public ManageUrlsUseCase manageUrlsUseCase(UrlRepository urlRepository,
            CacheRepository cacheRepository) {
        return new ManageUrlsService(urlRepository, cacheRepository);
    }

    @Bean
    public SessionStore sessionStore(ReactiveRedisTemplate<String, String> redis) {
        return new RedisSessionStore(redis);
    }

    @Bean
    public UniqueVisitorCounter uniqueVisitorCounter(ReactiveRedisTemplate<String, String> redis) {
        return new RedisUniqueVisitorCounter(redis);
    }

    @Bean
    public AuthUseCase authUseCase(UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionStore sessionStore,
            IdGenerator idGenerator,
            AuditLog auditLog) {
        return new AuthService(userRepository, passwordHasher, sessionStore, idGenerator, auditLog);
    }

    @Bean
    public GetAnalyticsUseCase getAnalyticsUseCase(ClickEventRepository clickEventRepository,
            UniqueVisitorCounter uniqueVisitorCounter,
            UserRepository userRepository) {
        return new AnalyticsQueryService(clickEventRepository, uniqueVisitorCounter, userRepository);
    }

    @Bean
    public ManageDomainsUseCase manageDomainsUseCase(CustomDomainRepository customDomainRepository,
            IdGenerator idGenerator,
            DnsVerifier dnsVerifier,
            AuditLog auditLog) {
        return new DomainService(customDomainRepository, idGenerator, dnsVerifier, auditLog);
    }
}