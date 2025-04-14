package com.urlshortener.domain.model;

public record Actor(Long userId, boolean authenticated, boolean admin) {

    public static Actor guest() {
        return new Actor(null, false, false);
    }

    public static Actor user(long userId) {
        return new Actor(userId, true, false);
    }

    public static Actor admin(long userId) {
        return new Actor(userId, true, true);
    }
}
