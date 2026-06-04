package com.bofa.transaction;

import java.util.Optional;

/** Persistence boundary for booked transactions. */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    long count();
}
