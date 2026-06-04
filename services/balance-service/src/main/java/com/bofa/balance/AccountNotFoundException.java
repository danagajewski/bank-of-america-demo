package com.bofa.balance;

/** Raised when a balance inquiry references an unknown account. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
