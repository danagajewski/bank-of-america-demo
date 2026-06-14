package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTransactionRepositoryTest {

    private InMemoryTransactionRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryTransactionRepository();
    }

    private Transaction txn(String id, String idempotencyKey) {
        return new Transaction(id, idempotencyKey, "1234567890", "9876543210",
                new BigDecimal("50"), "USD", TransactionStatus.POSTED, Instant.now());
    }

    @Test
    void save_returnsSameTransaction() {
        Transaction tx = txn("id-1", "key-1");
        assertSame(tx, repo.save(tx));
    }

    @Test
    void findByIdempotencyKey_presentAfterSave() {
        Transaction tx = txn("id-1", "key-1");
        repo.save(tx);
        Optional<Transaction> result = repo.findByIdempotencyKey("key-1");
        assertTrue(result.isPresent());
        assertSame(tx, result.get());
    }

    @Test
    void findByIdempotencyKey_absentForUnknownKey() {
        assertTrue(repo.findByIdempotencyKey("no-such-key").isEmpty());
    }

    @Test
    void findByIdempotencyKey_nullKey_returnsEmpty() {
        assertTrue(repo.findByIdempotencyKey(null).isEmpty());
    }

    @Test
    void save_nullIdempotencyKey_doesNotIndexByKey() {
        Transaction tx = txn("id-1", null);
        repo.save(tx);
        assertEquals(1, repo.count());
        assertTrue(repo.findByIdempotencyKey(null).isEmpty());
    }

    @Test
    void count_reflectsNumberOfSavedTransactions() {
        assertEquals(0, repo.count());
        repo.save(txn("id-1", "key-1"));
        assertEquals(1, repo.count());
        repo.save(txn("id-2", "key-2"));
        assertEquals(2, repo.count());
    }
}
