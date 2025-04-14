package com.urlshortener.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
                @NotBlank @Email(message = "must be a valid email") String email,
                @NotBlank String password) {
}
