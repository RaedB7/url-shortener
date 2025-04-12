package com.urlshortener.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.model.ClickEvent;
import com.urlshortener.domain.port.outbound.ClickEventRepository;

import reactor.core.publisher.Mono;

@Repository
class R2dbcClickEventRepository implements ClickEventRepository {

    private final DatabaseClient databaseClient;

    R2dbcClickEventRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> saveAll(List<ClickEvent> events) {
        if (events.isEmpty()) {
            return Mono.empty();
        }

        StringBuilder sql = new StringBuilder(
                "INSERT INTO click_events (url_code, clicked_at, referrer, ip_hash, device_type, browser, os) VALUES ");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0)
                sql.append(", ");
            sql.append("(:code").append(i)
                    .append(", :time").append(i)
                    .append(", :ref").append(i)
                    .append(", :ip").append(i)
                    .append(", :device").append(i)
                    .append(", :browser").append(i)
                    .append(", :os").append(i).append(")");
        }
        sql.append(" ON CONFLICT (url_code, clicked_at, ip_hash) DO NOTHING");

        var spec = databaseClient.sql(sql.toString());
        for (int i = 0; i < events.size(); i++) {
            ClickEvent e = events.get(i);
            spec = spec.bind("code" + i, e.getUrlCode())
                    .bind("time" + i, e.getClickedAt())
                    .bind("ref" + i, orEmpty(e.getReferrer()))
                    .bind("ip" + i, e.getIpHash())
                    .bind("device" + i, orUnknown(e.getDeviceType()))
                    .bind("browser" + i, orUnknown(e.getBrowser()))
                    .bind("os" + i, orUnknown(e.getOs()));
        }

        return spec.fetch().rowsUpdated().then();
    }

    private Object orEmpty(String value) {
        return value == null ? "" : value;
    }

    private Object orUnknown(String value) {
        return value == null ? "Unknown" : value;
    }

    @Override
    public Mono<ClickStats> aggregateByCode(String code, Instant from, Instant to) {
        Mono<Long> totalClicks = databaseClient.sql("""
                SELECT COUNT(*) AS total_clicks
                FROM click_events
                WHERE url_code = :code AND clicked_at >= :from AND clicked_at <= :to
                """)
                .bind("code", code).bind("from", from).bind("to", to)
                .fetch().one()
                .map(row -> ((Number) row.get("total_clicks")).longValue());

        Mono<List<DailyCount>> timeline = databaseClient.sql("""
                SELECT TO_CHAR(clicked_at, 'YYYY-MM-DD') AS day, COUNT(*) AS clicks
                FROM click_events
                WHERE url_code = :code AND clicked_at >= :from AND clicked_at <= :to
                GROUP BY day ORDER BY day
                """)
                .bind("code", code).bind("from", from).bind("to", to)
                .fetch().all()
                .map(row -> new DailyCount(
                        (String) row.get("day"),
                        ((Number) row.get("clicks")).longValue()))
                .collectList();

        Mono<List<ReferrerCount>> referrers = databaseClient.sql("""
                SELECT COALESCE(NULLIF(referrer, ''), 'Direct') AS ref, COUNT(*) AS clicks
                FROM click_events
                WHERE url_code = :code AND clicked_at >= :from AND clicked_at <= :to
                GROUP BY ref ORDER BY clicks DESC LIMIT 10
                """)
                .bind("code", code).bind("from", from).bind("to", to)
                .fetch().all()
                .map(row -> new ReferrerCount(
                        (String) row.get("ref"),
                        ((Number) row.get("clicks")).longValue()))
                .collectList();

        Mono<List<DeviceCount>> devices = databaseClient.sql("""
                SELECT
                    CASE WHEN device_type = 'mobile' THEN 'mobile'
                         WHEN device_type = 'tablet' THEN 'tablet'
                         ELSE 'desktop' END AS device,
                    COUNT(*) AS clicks
                FROM click_events
                WHERE url_code = :code AND clicked_at >= :from AND clicked_at <= :to
                GROUP BY device
                """)
                .bind("code", code).bind("from", from).bind("to", to)
                .fetch().all()
                .map(row -> new DeviceCount(
                        (String) row.get("device"),
                        ((Number) row.get("clicks")).longValue()))
                .collectList();

        Mono<List<BrowserCount>> browsers = databaseClient.sql("""
                SELECT
                    CASE WHEN browser IN ('Chrome','Safari','Firefox','Edge') THEN browser
                         ELSE 'Other' END AS browser,
                    COUNT(*) AS clicks
                FROM click_events
                WHERE url_code = :code AND clicked_at >= :from AND clicked_at <= :to
                GROUP BY browser
                """)
                .bind("code", code).bind("from", from).bind("to", to)
                .fetch().all()
                .map(row -> new BrowserCount(
                        (String) row.get("browser"),
                        ((Number) row.get("clicks")).longValue()))
                .collectList();

        return Mono.zip(totalClicks, timeline, referrers, devices, browsers)
                .map(tuple -> new ClickStats(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), tuple.getT5()));
    }

    @Override
    public Mono<OverviewStats> aggregateOverview() {
        return databaseClient.sql("""
                WITH top AS (
                    SELECT url_code, COUNT(*) AS c
                    FROM click_events
                    GROUP BY url_code
                    ORDER BY c DESC
                    LIMIT 1
                )
                SELECT
                    (SELECT COUNT(*) FROM urls WHERE deleted_at IS NULL) AS total_links,
                    (SELECT COUNT(*) FROM click_events) AS total_clicks,
                    (SELECT url_code FROM top) AS top_url,
                    (SELECT c FROM top) AS top_clicks
                """)
                .fetch().one()
                .map(row -> new OverviewStats(
                        ((Number) row.get("total_links")).longValue(),
                        ((Number) row.get("total_clicks")).longValue(),
                        row.get("top_url") != null ? (String) row.get("top_url") : "N/A",
                        row.get("top_clicks") != null ? ((Number) row.get("top_clicks")).longValue() : 0L));
    }
}
