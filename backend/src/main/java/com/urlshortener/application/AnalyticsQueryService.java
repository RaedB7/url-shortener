package com.urlshortener.application;

import com.urlshortener.domain.port.inbound.GetAnalyticsUseCase;
import com.urlshortener.domain.port.outbound.ClickEventRepository;
import com.urlshortener.domain.port.outbound.UniqueVisitorCounter;
import com.urlshortener.domain.port.outbound.UserRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsQueryService implements GetAnalyticsUseCase {

    private final ClickEventRepository clickEventRepository;
    private final UniqueVisitorCounter uniqueVisitorCounter;
    private final UserRepository userRepository;

    public AnalyticsQueryService(ClickEventRepository clickEventRepository,
            UniqueVisitorCounter uniqueVisitorCounter,
            UserRepository userRepository) {
        this.clickEventRepository = clickEventRepository;
        this.uniqueVisitorCounter = uniqueVisitorCounter;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<LinkStats> statsFor(String code, Instant from, Instant to) {
        Instant effectiveFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveTo = to != null ? to : Instant.now();

        Mono<Long> uniquesTotal = uniqueVisitorCounter.uniquesTotal(code).defaultIfEmpty(0L);

        return clickEventRepository.aggregateByCode(code, effectiveFrom, effectiveTo)
                .zipWith(uniquesTotal)
                .flatMap(tuple -> {
                    var stats = tuple.getT1();
                    long totalUniques = tuple.getT2();
                    return dailyUniques(code, stats.timeline())
                            .map(dailyUniques -> new LinkStats(
                                    code,
                                    stats.totalClicks(),
                                    totalUniques,
                                    mergeTimeline(stats.timeline(), dailyUniques),
                                    toReferrerCounts(stats.referrers()),
                                    toDeviceCounts(stats.devices()),
                                    toBrowserCounts(stats.browsers())));
                });
    }

    private Mono<Map<String, Long>> dailyUniques(String code, List<ClickEventRepository.DailyCount> timeline) {
        List<Mono<Void>> lookups = new ArrayList<>();
        Map<String, Long> result = new HashMap<>();
        for (var day : timeline) {
            lookups.add(uniqueVisitorCounter.uniquesForDay(code, day.day())
                    .defaultIfEmpty(0L)
                    .doOnNext(count -> result.put(day.day(), count))
                    .then());
        }
        return Mono.when(lookups).thenReturn(result);
    }

    private List<DayPoint> mergeTimeline(List<ClickEventRepository.DailyCount> timeline,
            Map<String, Long> dailyUniques) {
        return timeline.stream()
                .map(day -> new DayPoint(day.day(), day.clicks(),
                        dailyUniques.getOrDefault(day.day(), 0L)))
                .toList();
    }

    private List<NameCount> toReferrerCounts(List<ClickEventRepository.ReferrerCount> counts) {
        return counts.stream().map(c -> new NameCount(c.referrer(), c.count())).toList();
    }

    private List<NameCount> toDeviceCounts(List<ClickEventRepository.DeviceCount> counts) {
        return counts.stream().map(c -> new NameCount(c.device(), c.count())).toList();
    }

    private List<NameCount> toBrowserCounts(List<ClickEventRepository.BrowserCount> counts) {
        return counts.stream().map(c -> new NameCount(c.browser(), c.count())).toList();
    }

    @Override
    public Mono<OverviewStats> overview() {
        return userRepository.count()
                .zipWith(clickEventRepository.aggregateOverview())
                .map(tuple -> new OverviewStats(
                        tuple.getT1(),
                        tuple.getT2().totalLinks(),
                        tuple.getT2().totalClicks(),
                        tuple.getT2().topUrl(),
                        tuple.getT2().topUrlClicks()));
    }
}