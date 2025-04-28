package com.urlshortener.infrastructure.web;

import com.urlshortener.domain.exception.AccountSuspendedException;
import com.urlshortener.domain.exception.AliasAlreadyExistsException;
import com.urlshortener.domain.exception.DomainAlreadyExistsException;
import com.urlshortener.domain.exception.EmailTakenException;
import com.urlshortener.domain.exception.ForbiddenException;
import com.urlshortener.domain.exception.InvalidUrlException;
import com.urlshortener.domain.exception.RateLimitExceededException;
import com.urlshortener.domain.exception.UnauthorizedException;
import com.urlshortener.domain.exception.UrlExpiredException;
import com.urlshortener.domain.exception.UrlNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UrlNotFoundException.class)
    public ProblemDetail handleUrlNotFound(UrlNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Link Not Found", ex.getMessage(),
                "https://urlshortener.dev/errors/not-found");
    }

    @ExceptionHandler(UrlExpiredException.class)
    public ProblemDetail handleUrlExpired(UrlExpiredException ex) {
        return problem(HttpStatus.GONE, "Link Expired", ex.getMessage(),
                "https://urlshortener.dev/errors/expired");
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid URL", ex.getMessage(),
                "https://urlshortener.dev/errors/invalid-url");
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ProblemDetail handleAliasExists(AliasAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Alias Already Exists", ex.getMessage(),
                "https://urlshortener.dev/errors/alias-exists");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail p = problem(HttpStatus.TOO_MANY_REQUESTS, "Rate Limit Exceeded",
                ex.getMessage(), "https://urlshortener.dev/errors/rate-limited");
        p.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return p;
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Login Required", ex.getMessage(),
                "https://urlshortener.dev/errors/unauthorized");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(),
                "https://urlshortener.dev/errors/forbidden");
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ProblemDetail handleSuspended(AccountSuspendedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Account Suspended", ex.getMessage(),
                "https://urlshortener.dev/errors/suspended");
    }

    @ExceptionHandler(EmailTakenException.class)
    public ProblemDetail handleEmailTaken(EmailTakenException ex) {
        return problem(HttpStatus.CONFLICT, "Email Already Registered", ex.getMessage(),
                "https://urlshortener.dev/errors/email-taken");
    }

    @ExceptionHandler(DomainAlreadyExistsException.class)
    public ProblemDetail handleDomainExists(DomainAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Domain Already Registered", ex.getMessage(),
                "https://urlshortener.dev/errors/domain-taken");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleBind(WebExchangeBindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request", detail,
                "https://urlshortener.dev/errors/validation");
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ProblemDetail handleBadDate(DateTimeParseException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid expiresAt",
                "expiresAt must be an ISO-8601 instant (e.g. 2026-12-31T23:59:59Z)",
                "https://urlshortener.dev/errors/invalid-date");
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleUnreadableBody(ServerWebInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request",
                "Request body is not valid JSON",
                "https://urlshortener.dev/errors/malformed");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred",
                "https://urlshortener.dev/errors/internal");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setType(URI.create(type));
        return p;
    }
}
