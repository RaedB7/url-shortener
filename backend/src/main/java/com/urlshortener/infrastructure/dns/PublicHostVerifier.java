package com.urlshortener.infrastructure.dns;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.urlshortener.application.CreateShortUrlService;
import com.urlshortener.domain.exception.InvalidUrlException;
import com.urlshortener.domain.model.OriginalUrl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PublicHostVerifier implements CreateShortUrlService.HostPolicy {

    private static final Logger log = LoggerFactory.getLogger(PublicHostVerifier.class);
    private static final Logger abuseLog = LoggerFactory.getLogger("abuse");

    private final Cache<String, Boolean> verdicts = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Counter ssrfBlocked;

    public PublicHostVerifier(MeterRegistry meterRegistry) {
        this.ssrfBlocked = Counter.builder("urlshortener_abuse_blocked_total")
                .description("Requests blocked by the SSRF guard")
                .tag("reason", "ssrf")
                .register(meterRegistry);
    }

    public Mono<OriginalUrl> assertPublic(OriginalUrl url) {
        String host = URI.create(url.getValue()).getHost();
        if (host == null) {
            return Mono.error(new InvalidUrlException("URL must have a valid hostname"));
        }

        Boolean cached = verdicts.getIfPresent(host);
        if (cached != null) {
            return cached ? Mono.just(url)
                    : Mono.error(new InvalidUrlException(
                            "URL points to a private or internal address, which is not allowed"));
        }

        return Mono.fromCallable(() -> {
            boolean publicHost = isPublic(host);
            verdicts.put(host, publicHost);
            return publicHost;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(isPublic -> isPublic
                        ? Mono.just(url)
                        : Mono.error(new InvalidUrlException(
                                "URL points to a private or internal address, which is not allowed")));
    }

    public Mono<Boolean> isPublicCached(String host) {
        Boolean cached = verdicts.getIfPresent(host);
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.fromCallable(() -> {
            boolean publicHost = isPublic(host);
            verdicts.put(host, publicHost);
            return publicHost;
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public void block(String host, String code) {
        ssrfBlocked.increment();
        abuseLog.warn("blocked redirect to private host {} via link {}", host, code);
        log.debug("SSRF block: host={} code={}", host, code);
    }

    private boolean isPublic(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress addr : addresses) {
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                return false;
            }
        }
        return true;
    }

    public Mono<Boolean> hasTxtRecord(String domain, String expectedToken) {
        return Mono.fromCallable(() -> {
            Lookup lookup = new Lookup(domain, Type.TXT);
            lookup.run();
            if (lookup.getResult() != Lookup.SUCCESSFUL) {
                return false;
            }
            for (Record r : lookup.getAnswers()) {
                String txt = r.rdataToString().replace("\"", "");
                if (txt.equals("urlshortener-verify=" + expectedToken)) {
                    return true;
                }
            }
            return false;
        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
