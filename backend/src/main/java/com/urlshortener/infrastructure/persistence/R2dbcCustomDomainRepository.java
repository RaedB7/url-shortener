package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.model.CustomDomain;
import com.urlshortener.domain.port.outbound.CustomDomainRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
class R2dbcCustomDomainRepository implements CustomDomainRepository {

    private final R2dbcEntityTemplate template;

    R2dbcCustomDomainRepository(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<CustomDomain> save(CustomDomain domain) {
        CustomDomainEntity entity = toEntity(domain);
        return template.insert(entity).map(this::toDomain);
    }

    @Override
    public Mono<CustomDomain> findById(long id) {
        return template.select(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .one()
                .map(this::toDomain);
    }

    @Override
    public Mono<CustomDomain> findByDomain(String domain) {
        return template.select(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("domain").is(domain)))
                .one()
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByDomain(String domain) {
        return template.select(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("domain").is(domain)))
                .count()
                .map(count -> count > 0);
    }

    @Override
    public Mono<CustomDomain> setVerified(long id, boolean verified) {
        return template.update(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .apply(Update.update("verified", verified))
                .then(template.select(CustomDomainEntity.class)
                        .matching(Query.query(Criteria.where("id").is(id)))
                        .one()
                        .map(this::toDomain));
    }

    @Override
    public Flux<CustomDomain> findAllByOwner(long ownerId) {
        return template.select(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("owner_id").is(ownerId)))
                .all()
                .map(this::toDomain);
    }

    @Override
    public Flux<CustomDomain> findAll() {
        return template.select(CustomDomainEntity.class).all().map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteById(long id) {
        return template.delete(CustomDomainEntity.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .all()
                .then();
    }

    private CustomDomainEntity toEntity(CustomDomain domain) {
        CustomDomainEntity entity = new CustomDomainEntity();
        entity.setId(domain.getId());
        entity.setDomain(domain.getDomain());
        entity.setOwnerId(domain.getOwnerId());
        entity.setVerifyToken(domain.getVerifyToken());
        entity.setVerified(domain.isVerified());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    private CustomDomain toDomain(CustomDomainEntity entity) {
        return new CustomDomain(
                entity.getId(),
                entity.getDomain(),
                entity.getOwnerId(),
                entity.getVerifyToken(),
                Boolean.TRUE.equals(entity.getVerified()),
                entity.getCreatedAt()
        );
    }
}