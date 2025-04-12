package com.urlshortener.domain.port.outbound;

public interface PasswordHasher {

    String hash(String raw);

    boolean matches(String raw, String hash);
}
