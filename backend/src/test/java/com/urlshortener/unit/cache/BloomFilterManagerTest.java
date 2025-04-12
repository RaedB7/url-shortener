package com.urlshortener.unit.cache;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.UrlRepository;
import com.urlshortener.infrastructure.cache.BloomFilterManager;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterManagerTest {

    @Test
    void rejectsUnknownCodesOnlyAfterSeeding() {
        FakeRepo repo = new FakeRepo(List.of("abc", "def"));
        BloomFilterManager manager = new BloomFilterManager(repo, new AppProperties());

        assertFalse(manager.isInitialized());
        assertFalse(manager.mightContain("abc"));

        manager.rebuild().block();

        assertTrue(manager.isInitialized());
        assertTrue(manager.mightContain("abc"));
        assertTrue(manager.mightContain("def"));
        assertFalse(manager.mightContain("never-seen-before"));
    }

    @Test
    void addedCodesAreVisibleWithoutReseed() {
        FakeRepo repo = new FakeRepo(List.of());
        BloomFilterManager manager = new BloomFilterManager(repo, new AppProperties());
        manager.rebuild().block();

        manager.add("fresh-code");
        assertTrue(manager.mightContain("fresh-code"));
    }

    private static final class FakeRepo implements UrlRepository, org.springframework.beans.factory.ObjectProvider<UrlRepository> {

        private final List<String> codes;

        FakeRepo(List<String> codes) {
            this.codes = new ArrayList<>(codes);
        }

        @Override
        public UrlRepository getObject() {
            return this;
        }

        @Override
        public UrlRepository getObject(Object... args) {
            return this;
        }

        @Override
        public UrlRepository getIfAvailable() {
            return this;
        }

        @Override
        public UrlRepository getIfUnique() {
            return this;
        }

        @Override
        public Flux<String> findAllLiveCodes() {
            return Flux.fromIterable(codes);
        }

        @Override
        public reactor.core.publisher.Mono<Url> save(Url url) {
            return reactor.core.publisher.Mono.just(url);
        }

        @Override
        public reactor.core.publisher.Mono<Url> findByCode(String code) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Boolean> existsByCode(String code) {
            return reactor.core.publisher.Mono.just(false);
        }

        @Override
        public reactor.core.publisher.Mono<Void> softDeleteByCode(String code) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<Page<Url>> findAllByOwner(Long ownerId, int page, int size, String search) {
            return reactor.core.publisher.Mono.just(new Page<>(List.of(), page, size, 0, 0));
        }

        @Override
        public reactor.core.publisher.Mono<Page<Url>> findAll(int page, int size, String search) {
            return reactor.core.publisher.Mono.just(new Page<>(List.of(), page, size, 0, 0));
        }
    }
}
