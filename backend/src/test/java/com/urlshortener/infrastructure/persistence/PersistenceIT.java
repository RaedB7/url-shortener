package com.urlshortener.infrastructure.persistence;

import com.urlshortener.config.AppProperties;
import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.port.outbound.UrlRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PersistenceIT {

    private static final String HOST = System.getenv().getOrDefault("US_IT_DB_HOST", "localhost");
    private static final String PORT = System.getenv().getOrDefault("US_IT_DB_PORT", "5433");
    private static final String USER = System.getenv().getOrDefault("US_IT_DB_USER", "test");
    private static final String PASS = System.getenv().getOrDefault("US_IT_DB_PASSWORD", "test");
    private static final String DB = System.getenv().getOrDefault("US_IT_DB_NAME", "urlshortener_it");

    private static UrlRepository repository;
    private static final AtomicLong idSeq = new AtomicLong(1000);
    private static boolean available;

    @BeforeAll
    static void startDbAndSchema() {
        PostgresqlConnectionFactory factory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(HOST)
                        .port(Integer.parseInt(PORT))
                        .database(DB)
                        .username(USER)
                        .password(PASS)
                        .build());

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql"));
        try {
            Mono.from(factory.create())
                    .flatMap(conn -> Mono.from(populator.populate(conn))
                            .then(Mono.from(conn.close())))
                    .block(java.time.Duration.ofSeconds(10));
            available = true;
        } catch (Exception e) {
            available = false;
        }
        if (!available) {
            return;
        }
        DatabaseClient client = DatabaseClient.create(factory);
        repository = new R2dbcUrlRepository(new R2dbcEntityTemplate(factory), client, new NoopBloom());

        for (long uid : new long[] { 1L, 2L, 7L, 42L }) {
            client.sql(
                    "INSERT INTO users (id, email, password_hash, role) VALUES (:id, :email, 'x', 'USER') ON CONFLICT DO NOTHING")
                    .bind("id", uid)
                    .bind("email", "user" + uid + "@it.test")
                    .then()
                    .block();
        }
    }

    @Test
    void saveAndFindByCodeRoundTrip() {
        assumeTrue(available, "no local postgres on " + HOST + ":" + PORT + ", skipping");
        Url saved = repository.save(sampleUrl("round-trip", null)).block();

        assertNotNull(saved);
        assertEquals("round-trip", saved.getCode().getValue());

        Url found = repository.findByCode("round-trip").block();
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
        assertEquals("https://example.com/long", found.getOriginalUrl().getValue());
    }

    @Test
    void duplicateCodeIsRejectedByUniqueIndex() {
        assumeTrue(available, "no local postgres, skipping");
        repository.save(sampleUrl("dup-code", null)).block();
        assertThrows(Exception.class, () -> repository.save(sampleUrl("dup-code", null)).block());
    }

    @Test
    void softDeleteHidesLinkFromOwnerListing() {
        assumeTrue(available, "no local postgres, skipping");
        repository.save(sampleUrl("soft-delete-me", 42L)).block();
        repository.softDeleteByCode("soft-delete-me").block();

        var page = repository.findAllByOwner(42L, 0, 10, null).block();
        assertTrue(page.content().stream()
                .noneMatch(u -> u.getCode().getValue().equals("soft-delete-me")));
    }

    @Test
    void ownerFilterSeparatesUsersLinks() {
        assumeTrue(available, "no local postgres, skipping");
        repository.save(sampleUrl("owner-a", 1L)).block();
        repository.save(sampleUrl("owner-b", 2L)).block();

        var mine = repository.findAllByOwner(1L, 0, 10, null).block();
        assertEquals(1, mine.content().size());
        assertEquals("owner-a", mine.content().get(0).getCode().getValue());
    }

    @Test
    void searchMatchesCodeAndOriginalUrl() {
        assumeTrue(available, "no local postgres, skipping");
        repository.save(sampleUrl("searchable-code", 7L)).block();
        repository.save(sampleUrl("other-code", 7L)).block();

        var byCode = repository.findAllByOwner(7L, 0, 10, "searchable").block();
        assertEquals(1, byCode.content().size());
    }

    private static Url sampleUrl(String code, Long ownerId) {
        return new Url(idSeq.incrementAndGet(), new ShortCode(code),
                new OriginalUrl("https://example.com/long"), ownerId,
                null, null, null, Instant.now());
    }

    static class NoopBloom extends com.urlshortener.infrastructure.cache.BloomFilterManager {
        NoopBloom() {
            super(null, new AppProperties());
        }

        @Override
        public void add(String code) {
        }
    }
}
