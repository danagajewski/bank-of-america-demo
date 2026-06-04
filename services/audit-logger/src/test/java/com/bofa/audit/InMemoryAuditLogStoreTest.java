package com.bofa.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Baseline happy-path coverage for the audit store. Note: the security-relevant
 * paths (failed-auth logging, severity classification, timestamp stamping) are
 * intentionally NOT yet covered.
 */
class InMemoryAuditLogStoreTest {

    @Test
    void savesAndReturnsEvent() {
        InMemoryAuditLogStore store = new InMemoryAuditLogStore();
        AuditEvent event = AuditEvent.builder()
                .type(AuditEventType.TRANSACTION_CREATED)
                .actor("teller-007")
                .resourceId("txn-1")
                .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        store.save(event);

        assertEquals(1, store.size());
        assertTrue(store.findAll().contains(event));
    }
}
