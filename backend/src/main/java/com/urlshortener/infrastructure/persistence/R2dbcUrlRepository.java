package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.UrlRepository;
import com.urlshortener.infrastructure.cache.BloomFilterManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Repository
class R2dbcUrlRepository implements UrlRepository {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;
    private final BloomFilterManager bloomFilter;

    R2dbcUrlRepository(R2dbcEntityTemplate template, DatabaseClient databaseClient,
            BloomFilterManager bloomFilter) {
        this.template = template;
        this.databaseClient = databaseClient;
        this.bloomFilter = bloomFilter;
    }

    @Override
    public Mono<Url> save(Url url) {
        UrlEntity entity = toEntity(url);
        return template.insert(entity)
                .doOnSuccess(saved -> bloomFilter.add(saved.getCode()))
                .map(this::toDomain);
    }

    @Override
    public Mono<Url> findByCode(String code) {
        return template.select(UrlEntity.class)
                .matching(Query.query(Criteria.where("code").is(code)))
                .one()
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByCode(String code) {
        return template.select(UrlEntity.class)
                .matching(Query.query(Criteria.where("code").is(code)))
                .count()
                .map(count -> count > 0);
    }

    @Override
    public Mono<Void> softDeleteByCode(String code) {
        return template.update(UrlEntity.class)
                .matching(Query.query(Criteria.where("code").is(code)))
                .apply(Update.update("deleted_at", Instant.now()))
                .then();
    }

    @Override
    public Mono<Url> updateMeta(String code, Instant expiresAt, String passwordHash, boolean removePassword) {
        boolean hasExpiry = expiresAt != null;
        boolean hasPassword = removePassword || passwordHash != null;
        if (!hasExpiry && !hasPassword) {
            return findByCode(code);
        }
        Update update = hasExpiry ? Update.update("expires_at", expiresAt) : Update.update("password_hash", "");
        if (hasExpiry && hasPassword) {
            update = update.set("password_hash", removePassword ? null : passwordHash);
        }
        return template.update(UrlEntity.class)
                .matching(Query.query(Criteria.where("code").is(code)))
                .apply(update)
                .then(findByCode(code));
    }

    @Override
    public Mono<Page<Url>> findAllByOwner(Long ownerId, int page, int size, String search) {
        Criteria criteria = Criteria.where("owner_id").is(ownerId).and(notDeleted());
        criteria = withSearch(criteria, search);

        return pageQuery(criteria, page, size);
    }

    @Override
    public Mono<Page<Url>> findAll(int page, int size, String search) {
        Criteria criteria = notDeleted();
        criteria = withSearch(criteria, search);

        return pageQuery(criteria, page, size);
    }

    @Override
    public Flux<String> findAllLiveCodes() {
        return databaseClient.sql(
                "SELECT code FROM urls WHERE deleted_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())")
                .fetch()
                .all()
                .map(row -> (String) row.get("code"));
    }

    private Criteria notDeleted() {
        return Criteria.where("deleted_at").isNull();
    }

    private Criteria withSearch(Criteria criteria, String search) {
        if (search == null || search.isBlank()) {
            return criteria;
        }
        return criteria.and(Criteria.where("original_url").like("%" + search + "%")
                .or(Criteria.where("code").like("%" + search + "%")));
    }

    private Mono<Page<Url>> pageQuery(Criteria criteria, int page, int size) {
        Query query = Query.query(criteria)
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at")));

        Mono<List<Url>> content = template.select(UrlEntity.class)
                .matching(query)
                .all()
                .map(this::toDomain)
                .collectList();

        Mono<Long> total = template.select(UrlEntity.class)
                .matching(Query.query(criteria))
                .count();

        return Mono.zip(content, total)
                .map(tuple -> new Page<>(
                        tuple.getT1(), page, size, tuple.getT2(),
                        (int) Math.ceil((double) tuple.getT2() / size)));
    }

    private UrlEntity toEntity(Url url) {
        UrlEntity entity = new UrlEntity();
        entity.setId(url.getId());
        entity.setCode(url.getCode().getValue());
        entity.setOriginalUrl(url.getOriginalUrl().getValue());
        entity.setOwnerId(url.getOwnerId());
        entity.setPasswordHash(url.getPasswordHash());
        entity.setExpiresAt(url.getExpiresAt());
        entity.setDeletedAt(url.getDeletedAt());
        entity.setCreatedAt(url.getCreatedAt());
        return entity;
    }

    private Url toDomain(UrlEntity entity) {
        return new Url(
                entity.getId(),
                new ShortCode(entity.getCode()),
                new OriginalUrl(entity.getOriginalUrl()),
                entity.getOwnerId(),
                entity.getPasswordHash(),
                entity.getExpiresAt(),
                entity.getDeletedAt(),
                entity.getCreatedAt());
    }
}
