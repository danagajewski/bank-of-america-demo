package com.bofa.transaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory transaction store standing in for the on-prem ledger of record. */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> byId = new ConcurrentHashMap<>();
    private final Map<String, Transaction> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Transaction save(Transaction transaction) {
        byId.put(transaction.getId(), transaction);
        if (transaction.getIdempotencyKey() != null) {
            byIdempotencyKey.put(transaction.getIdempotencyKey(), transaction);
        }
        return transaction;
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public long count() {
        return byId.size();
    }
}
