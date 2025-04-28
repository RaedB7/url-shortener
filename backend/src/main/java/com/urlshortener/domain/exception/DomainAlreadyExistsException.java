package com.urlshortener.domain.exception;

public class DomainAlreadyExistsException extends RuntimeException {
    public DomainAlreadyExistsException(String domain) {
        super("The domain '" + domain + "' is already registered");
    }
}