package com.urlshortener.unit.domain;

import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.InvalidUrlException;
import com.urlshortener.domain.model.OriginalUrl;
import com.urlshortener.domain.model.ShortCode;
import com.urlshortener.domain.model.Url;
import com.urlshortener.domain.service.UrlRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UrlTest {

    @Test
    void linkIsExpiredOnlyAfterExpiry() {
        Url u = urlWithExpiry(Instant.now().minusSeconds(60));
        assertTrue(u.isExpired());
        assertFalse(urlWithExpiry(Instant.now().plusSeconds(60)).isExpired());
        assertFalse(new Url(1L, code("abc"), new OriginalUrl("https://example.com"),
                null, null, null, null, Instant.now()).isExpired());
    }

    @Test
    void deletedLinksAreSoftDeleted() {
        Url u = new Url(1L, code("abc"), new OriginalUrl("https://example.com"),
                5L, null, Instant.now().plusSeconds(60), Instant.now().minusSeconds(1), Instant.now());
        assertTrue(u.isDeleted());
    }

    @Test
    void ownershipIsExclusiveToOwner() {
        Url u = urlOwnedBy(5L);
        assertTrue(u.isOwnedBy(5L));
        assertFalse(u.isOwnedBy(6L));
    }

    @Test
    void guestLinkHasNoOwner() {
        Url u = new Url(1L, code("abc"), new OriginalUrl("https://example.com"),
                null, null, null, null, Instant.now());
        assertTrue(u.isGuestLink());
        assertFalse(urlOwnedBy(5L).isGuestLink());
    }

    @Test
    void guestCreateRequiresExpiry() {
        assertThrows(InvalidUrlException.class, () -> UrlRules.assertGuestCreateAllowed(null));
    }

    @Test
    void guestCreateRejectsExpiryBeyondSevenDays() {
        assertThrows(InvalidUrlException.class,
                () -> UrlRules.assertGuestCreateAllowed(Instant.now().plus(8, java.time.temporal.ChronoUnit.DAYS)));
    }

    @Test
    void guestCreateRejectsPastExpiry() {
        assertThrows(InvalidUrlException.class,
                () -> UrlRules.assertGuestCreateAllowed(Instant.now().minusSeconds(5)));
    }

    @Test
    void guestCreateAcceptsSaneExpiry() {
        assertDoesNotThrow(
                () -> UrlRules.assertGuestCreateAllowed(Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS)));
    }

    @Test
    void guestCannotUseReservedFeatures() {
        assertThrows(ForbiddenException.class, () -> UrlRules.assertAliasAllowed(false, "my-alias"));
        assertThrows(ForbiddenException.class, () -> UrlRules.assertPasswordAllowed(false, "pw"));
    }

    @Test
    void loggedInUserCanUseAliasAndPassword() {
        assertDoesNotThrow(() -> UrlRules.assertAliasAllowed(true, "my-alias"));
        assertDoesNotThrow(() -> UrlRules.assertPasswordAllowed(true, "pw"));
    }

    @Test
    void shortCodeRejectsBadInput() {
        assertThrows(InvalidUrlException.class, () -> new ShortCode(""));
        assertThrows(InvalidUrlException.class, () -> new ShortCode("toolongcode-toolongcode"));
        assertThrows(InvalidUrlException.class, () -> new ShortCode("bad code!"));
        assertThrows(InvalidUrlException.class, () -> new ShortCode("login"));
    }

    @Test
    void originalUrlRejectsDangerousProtocols() {
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("javascript:alert(1)"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("data:text/html,hi"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("file:///etc/passwd"));
    }

    @Test
    @SuppressWarnings("removal")
    void originalUrlRejectsPrivateIpLiterals() {
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("http://192.168.1.5/"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("http://10.0.0.1/"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("http://127.0.0.1:8080/"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("http://169.254.169.254/latest/meta-data"));
        assertThrows(InvalidUrlException.class, () -> new OriginalUrl("http://[::1]/"));
    }

    @Test
    void originalUrlAcceptsNormalUrls() {
        assertEquals("https://example.com/some/path?q=1",
                new OriginalUrl("https://example.com/some/path?q=1").getValue());
    }

    private Url urlWithExpiry(Instant expiresAt) {
        return new Url(1L, code("abc"), new OriginalUrl("https://example.com"),
                null, null, expiresAt, null, Instant.now());
    }

    private Url urlOwnedBy(Long ownerId) {
        return new Url(1L, code("abc"), new OriginalUrl("https://example.com"),
                ownerId, null, null, null, Instant.now());
    }

    private ShortCode code(String v) {
        return new ShortCode(v);
    }
}
