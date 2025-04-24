package com.urlshortener.domain.port.inbound;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface GetAnalyticsUseCase {

    Mono<LinkStats> statsFor(String code, Instant from, Instant to);

    Mono<OverviewStats> overview();

    record LinkStats(String code, long totalClicks, long uniqueVisitors,
            List<DayPoint> timeline, List<NameCount> referrers,
            List<NameCount> devices, List<NameCount> browsers) {
    }

    record DayPoint(String day, long clicks, long uniques) {
    }

    record NameCount(String name, long count) {
    }

    record OverviewStats(long totalUsers, long totalLinks, long totalClicks,
            String topUrl, long topUrlClicks) {
    }
}