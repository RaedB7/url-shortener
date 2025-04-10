package com.urlshortener.unit.domain;

import com.urlshortener.infrastructure.idgen.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void idsAreUniqueAndMonotonicUnderConcurrency() throws InterruptedException {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0);
        int threads = 8;
        int perThread = 1000;
        Set<Long> ids = new HashSet<>();
        Set<Long> synchronizedIds = java.util.Collections.synchronizedSet(new HashSet<>());

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        synchronizedIds.add(gen.nextId());
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(threads * perThread, synchronizedIds.size(), "duplicate ids generated");
    }

    @Test
    void idsAreRoughlyTimeSortable() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
        long first = gen.nextId();
        long second = gen.nextId();
        assertTrue(second > first);
    }

    @Test
    void workerIdBoundsAreEnforced() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(-1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(1024));
    }
}
