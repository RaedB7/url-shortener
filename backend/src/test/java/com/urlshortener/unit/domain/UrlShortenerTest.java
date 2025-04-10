package com.urlshortener.unit.domain;

import com.urlshortener.domain.service.UrlShortener;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlShortenerTest {

    private final UrlShortener shortener = new UrlShortener();

    @Test
    void encodeDecodeRoundTrip() {
        for (long id : new long[] { 0L, 1L, 61L, 62L, 3844L, 56800235584L }) {
            assertEquals(id, shortener.decode(shortener.encode(id)), "round trip failed for " + id);
        }
    }

    @Test
    void encodedCodesStayShort() {
        long yearOut = (1L << 46);
        assertTrue(shortener.encode(yearOut).length() <= 8);
    }

    @Test
    void decodeFlagsCustomAliases() {
        assertEquals(-1, shortener.decode("my-link"));
    }

    @Test
    void neighboringIdsProduceDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (long id = 1000; id < 1100; id++) {
            codes.add(shortener.encode(id));
        }
        assertEquals(100, codes.size());
    }
}
