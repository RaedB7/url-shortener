package com.urlshortener.infrastructure.cache;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.port.outbound.UrlRepository;

import reactor.core.publisher.Mono;

@Component
public class BloomFilterManager {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterManager.class);

    private final ObjectProvider<UrlRepository> urlRepositoryProvider;
    private final AppProperties properties;
    private final AtomicReference<BloomFilter<String>> filter = new AtomicReference<>();

    public BloomFilterManager(ObjectProvider<UrlRepository> urlRepositoryProvider, AppProperties properties) {
        this.urlRepositoryProvider = urlRepositoryProvider;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void init() {
        rebuild().subscribe(
                v -> log.info("Bloom filter initialized on startup"),
                err -> log.error("Bloom filter init failed (non-fatal): {}", err.getMessage()));
    }

    public boolean isInitialized() {
        return filter.get() != null;
    }

    public boolean mightContain(String code) {
        BloomFilter<String> current = filter.get();
        return current != null && current.mightContain(code);
    }

    public void add(String code) {
        BloomFilter<String> current = filter.get();
        if (current != null) {
            current.put(code);
        }
    }

    public long approximateSize() {
        BloomFilter<String> current = filter.get();
        return current != null ? current.approximateElementCount() : 0;
    }

    public Mono<Void> rebuild() {
        return urlRepositoryProvider.getObject().findAllLiveCodes()
                .collectList()
                .doOnNext(codes -> {
                    BloomFilter<String> seeded = BloomFilter.create(
                            Funnels.unencodedCharsFunnel(),
                            properties.getBloomFilter().getExpectedInsertions(),
                            properties.getBloomFilter().getFalsePositiveRate());
                    codes.forEach(seeded::put);
                    filter.set(seeded);
                    log.info("Bloom filter seeded with {} codes", codes.size());
                })
                .then();
    }
}
