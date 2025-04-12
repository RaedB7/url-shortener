package com.urlshortener.domain.port.outbound;

import java.time.Instant;
import java.util.List;

import com.urlshortener.domain.model.ClickEvent;

import reactor.core.publisher.Mono;

public interface ClickEventRepository {

    Mono<Void> saveAll(List<ClickEvent> events);

    Mono<ClickStats> aggregateByCode(String code, Instant from, Instant to);

    Mono<OverviewStats> aggregateOverview();

    record DailyCount(String day, long clicks) {
    }

    record DailyUniques(String day, long uniques) {
    }

    record ReferrerCount(String referrer, long count) {
    }

    record DeviceCount(String device, long count) {
    }

    record BrowserCount(String browser, long count) {
    }

    record ClickStats(long totalClicks, List<DailyCount> timeline,
            List<ReferrerCount> referrers, List<DeviceCount> devices,
            List<BrowserCount> browsers) {
    }

    record OverviewStats(long totalLinks, long totalClicks, String topUrl, long topUrlClicks) {
    }
}
