package com.urlshortener.domain.exception;

public class UrlExpiredException extends RuntimeException {

    private final String code;

    public UrlExpiredException(String code) {
        super("Link '" + code + "' has expired");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
