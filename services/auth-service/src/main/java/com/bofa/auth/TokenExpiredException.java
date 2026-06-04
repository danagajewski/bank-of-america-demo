package com.bofa.auth;

/** Raised when a session token has passed its expiry ({@code exp}) claim. */
public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
