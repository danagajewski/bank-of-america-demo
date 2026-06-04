package com.bofa.auth;

/** Raised when a session token is malformed or fails signature verification. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
