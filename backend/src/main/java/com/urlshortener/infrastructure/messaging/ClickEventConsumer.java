package com.urlshortener.infrastructure.messaging;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import com.urlshortener.domain.model.ClickEvent;
import com.urlshortener.domain.port.outbound.ClickEventRepository;
import com.urlshortener.domain.port.outbound.UniqueVisitorCounter;
import com.urlshortener.infrastructure.useragent.UserAgentParser;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);
    private static final String STREAM_KEY = "clicks";
    private static final int BATCH_SIZE = 100;
    private static final long MAX_STREAM_ENTRIES = 2000L;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final ReactiveRedisTemplate<String, String> redis;
    private final ClickEventRepository clickEventRepository;
    private final UserAgentParser userAgentParser;
    private final UniqueVisitorCounter uniqueVisitorCounter;
    private final Disposable subscription;
    private String lastId = "0";

    public ClickEventConsumer(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redis,
            ClickEventRepository clickEventRepository,
            UserAgentParser userAgentParser,
            UniqueVisitorCounter uniqueVisitorCounter,
            MeterRegistry meterRegistry) {
        this.redis = redis;
        this.clickEventRepository = clickEventRepository;
        this.userAgentParser = userAgentParser;
        this.uniqueVisitorCounter = uniqueVisitorCounter;

        meterRegistry.gauge("urlshortener_pipeline_lag_seconds",
                this, c -> c.pipelineLagSeconds());

        this.subscription = Flux.interval(POLL_INTERVAL)
                .flatMap(tick -> pollAndProcess())
                .onErrorContinue((ex, obj) -> log.error("Click consumer error: {}", ex.getMessage()))
                .subscribe();

        log.info("ClickEventConsumer started, polling every {}s", POLL_INTERVAL.getSeconds());
    }

    private Mono<Void> pollAndProcess() {
        Range<String> range = Range.of(Range.Bound.exclusive(lastId), Range.Bound.unbounded());
        return redis.opsForStream()
                .range(STREAM_KEY, range)
                .take(BATCH_SIZE)
                .collectList()
                .flatMap(records -> {
                    if (records.isEmpty())
                        return Mono.empty();

                    List<ClickEvent> events = new ArrayList<>();
                    List<Mono<Void>> hllRecords = new ArrayList<>();
                    for (var record : records) {
                        Map<String, String> fields = (Map<String, String>) (Map<?, ?>) record.getValue();
                        try {
                            UserAgentParser.UaResult ua = userAgentParser.parse(fields.get("userAgent"));
                            String ipHash = fields.getOrDefault("ipHash", "unknown");
                            ClickEvent event = new ClickEvent(
                                    fields.getOrDefault("urlCode", "unknown"),
                                    java.time.Instant.parse(fields.getOrDefault("clickedAt",
                                            java.time.Instant.now().toString())),
                                    fields.getOrDefault("referrer", ""),
                                    ipHash,
                                    ua.deviceType(), ua.browser(), ua.os());
                            events.add(event);

                            String dayKey = LocalDate.ofInstant(event.getClickedAt(), ZoneOffset.UTC).toString();
                            hllRecords.add(uniqueVisitorCounter.record(event.getUrlCode(), dayKey, ipHash));

                            lastId = record.getId().getValue();
                        } catch (Exception e) {
                            log.debug("Skipping malformed event: {}", e.getMessage());
                        }
                    }

                    log.debug("Processing {} click events", events.size());
                    return clickEventRepository.saveAll(events)
                            .then(Mono.when(hllRecords))
                            .then(redis.opsForStream().trim(STREAM_KEY, MAX_STREAM_ENTRIES))
                            .then();
                });
    }

    private long pipelineLagSeconds() {
        return 0;
    }

    @PreDestroy
    void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
