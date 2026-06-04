package com.bofa.transaction;

/** Lifecycle state of a money-movement transaction. */
public enum TransactionStatus {
    PENDING,
    POSTED,
    REJECTED
}
