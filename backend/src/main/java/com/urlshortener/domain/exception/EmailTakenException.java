package com.urlshortener.domain.exception;

public class EmailTakenException extends RuntimeException {
    public EmailTakenException(String email) {
        super("The email '" + email + "' is already registered");
    }
}
