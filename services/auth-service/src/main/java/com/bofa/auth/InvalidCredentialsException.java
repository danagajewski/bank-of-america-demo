package com.bofa.auth;

/** Raised when a login attempt fails username/password verification. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
