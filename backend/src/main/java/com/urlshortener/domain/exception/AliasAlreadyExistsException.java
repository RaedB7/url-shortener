package com.urlshortener.domain.exception;

public class AliasAlreadyExistsException extends RuntimeException {

    private final String alias;

    public AliasAlreadyExistsException(String alias) {
        super("The alias '" + alias + "' is already taken");
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }
}
