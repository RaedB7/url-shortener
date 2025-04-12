package com.urlshortener.infrastructure.persistence;

import com.urlshortener.domain.model.Role;
import com.urlshortener.domain.model.User;
import com.urlshortener.domain.port.outbound.UserRepository;
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

@Repository
class R2dbcUserRepository implements UserRepository {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    R2dbcUserRepository(R2dbcEntityTemplate template, DatabaseClient databaseClient) {
        this.template = template;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<User> save(User user) {
        UserEntity entity = toEntity(user);
        return template.insert(entity).map(this::toDomain);
    }

    @Override
    public Mono<User> findById(long id) {
        return template.select(UserEntity.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .one()
                .map(this::toDomain);
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return template.select(UserEntity.class)
                .matching(Query.query(Criteria.where("email").is(email)))
                .one()
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return template.select(UserEntity.class)
                .matching(Query.query(Criteria.where("email").is(email)))
                .count()
                .map(count -> count > 0);
    }

    @Override
    public Mono<User> setSuspended(long id, boolean suspended) {
        return template.update(UserEntity.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .apply(Update.update("suspended", suspended))
                .then(template.select(UserEntity.class)
                        .matching(Query.query(Criteria.where("id").is(id)))
                        .one()
                        .map(this::toDomain));
    }

    @Override
    public Flux<User> findAll(int page, int size) {
        return template.select(UserEntity.class)
                .matching(Query.empty().with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"))))
                .all()
                .map(this::toDomain);
    }

    @Override
    public Mono<Long> count() {
        return template.select(UserEntity.class).count();
    }

    @Override
    public Mono<Long> countLinksOwnedBy(long userId) {
        return databaseClient.sql("SELECT COUNT(*) AS c FROM urls WHERE owner_id = :userId AND deleted_at IS NULL")
                .bind("userId", userId)
                .fetch()
                .one()
                .map(row -> ((Number) row.get("c")).longValue());
    }

    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRole(user.getRole().name());
        entity.setSuspended(user.isSuspended());
        entity.setCreatedAt(user.getCreatedAt());
        return entity;
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                Role.valueOf(entity.getRole()),
                Boolean.TRUE.equals(entity.getSuspended()),
                entity.getCreatedAt());
    }
}
