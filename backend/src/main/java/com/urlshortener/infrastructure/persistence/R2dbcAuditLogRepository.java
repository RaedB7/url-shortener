package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.port.outbound.AuditLog;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
class R2dbcAuditLogRepository implements AuditLog {

    private final DatabaseClient databaseClient;

    R2dbcAuditLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> append(Long actorId, String actorEmail, String action, String target, String detail) {
        return databaseClient.sql("""
                        INSERT INTO audit_log (actor_id, actor_email, action, target, detail)
                        VALUES (:actorId, :actorEmail, :action, :target, :detail)
                        """)
                .bind("actorId", actorId)
                .bind("actorEmail", actorEmail)
                .bind("action", action)
                .bind("target", target)
                .bind("detail", detail)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Flux<Entry> latest(int limit, int offset) {
        return databaseClient.sql("""
                        SELECT actor_id, actor_email, action, target, detail, created_at
                        FROM audit_log
                        ORDER BY created_at DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .bind("limit", limit)
                .bind("offset", offset)
                .fetch()
                .all()
                .map(row -> new Entry(
                        (Long) row.get("actor_id"),
                        (String) row.get("actor_email"),
                        (String) row.get("action"),
                        (String) row.get("target"),
                        (String) row.get("detail"),
                        (Instant) row.get("created_at")));
    }

    @Override
    public Mono<Long> count() {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM audit_log")
                .fetch()
                .one()
                .map(row -> ((Number) row.get("c")).longValue());
    }
}
