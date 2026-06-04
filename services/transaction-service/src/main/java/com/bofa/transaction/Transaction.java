package com.bofa.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** A booked money-movement transaction. */
public final class Transaction {

    private final String id;
    private final String idempotencyKey;
    private final String sourceAccount;
    private final String destinationAccount;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionStatus status;
    private final Instant createdAt;

    public Transaction(String id,
                       String idempotencyKey,
                       String sourceAccount,
                       String destinationAccount,
                       BigDecimal amount,
                       String currency,
                       TransactionStatus status,
                       Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
