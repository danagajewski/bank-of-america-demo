package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionTest {

    @Test
    void allFieldsReturnedByGetters() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        Transaction tx = new Transaction(
                "id-1", "key-1", "1234567890", "9876543210",
                new BigDecimal("250.50"), "EUR", TransactionStatus.PENDING, now);

        assertEquals("id-1", tx.getId());
        assertEquals("key-1", tx.getIdempotencyKey());
        assertEquals("1234567890", tx.getSourceAccount());
        assertEquals("9876543210", tx.getDestinationAccount());
        assertEquals(new BigDecimal("250.50"), tx.getAmount());
        assertEquals("EUR", tx.getCurrency());
        assertEquals(TransactionStatus.PENDING, tx.getStatus());
        assertEquals(now, tx.getCreatedAt());
    }
}
