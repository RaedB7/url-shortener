package com.urlshortener.infrastructure.idgen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.urlshortener.domain.port.outbound.IdGenerator;

public class SnowflakeIdGenerator implements IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    private static final long CUSTOM_EPOCH = 1743465600000L;

    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final int WORKER_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(int workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "Worker ID must be between 0 and " + MAX_WORKER_ID + ", got: " + workerId);
        }
        this.workerId = workerId;
        log.info("SnowflakeIdGenerator initialized with worker ID {}", workerId);
    }

    @Override
    public synchronized long nextId() {
        long currentTimestamp = currentTimeMs();

        if (currentTimestamp < lastTimestamp) {
            log.warn("Clock moved backwards by {}ms, waiting for catch-up", lastTimestamp - currentTimestamp);
            while (currentTimestamp < lastTimestamp) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for clock catch-up", e);
                }
                currentTimestamp = currentTimeMs();
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long currentTimeMs() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMs();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMs();
        }
        return timestamp;
    }
}
