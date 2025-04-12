package com.urlshortener.infrastructure.web.dto;

import com.urlshortener.domain.model.Url;

public record UrlResponse(String code, String originalUrl, String shortUrl,
        String createdAt, String expiresAt, boolean passwordProtected) {

    public static UrlResponse from(Url url, String baseUrl) {
        return new UrlResponse(
                url.getCode().getValue(),
                url.getOriginalUrl().getValue(),
                baseUrl + "/" + url.getCode().getValue(),
                url.getCreatedAt().toString(),
                url.getExpiresAt() != null ? url.getExpiresAt().toString() : null,
                url.isPasswordProtected());
    }
}
