package com.bofa.transaction;

/** Raised when a structurally-valid transaction violates a business rule. */
public class TransactionRejectedException extends RuntimeException {
    public TransactionRejectedException(String message) {
        super(message);
    }
}
