package com.urlshortener.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUrlRequest(
                @NotBlank(message = "url is required") String url,
                String alias,
                String password,
                String expiresAt) {
}
