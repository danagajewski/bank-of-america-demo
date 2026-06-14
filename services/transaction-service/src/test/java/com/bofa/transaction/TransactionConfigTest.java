package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bofa.audit.AuditLogStore;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.InMemoryAuditLogStore;
import com.bofa.pii.PIIHandler;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class TransactionConfigTest {

    private final TransactionConfig config = new TransactionConfig();

    @Test
    void clock_returnsNonNull() {
        assertNotNull(config.clock());
    }

    @Test
    void auditLogStore_returnsInMemoryStore() {
        assertNotNull(config.auditLogStore());
    }

    @Test
    void auditLogger_returnsNonNull() {
        AuditLogStore store = new InMemoryAuditLogStore();
        Clock clock = config.clock();
        assertNotNull(config.auditLogger(store, clock));
    }

    @Test
    void piiHandler_returnsNonNull() {
        assertNotNull(config.piiHandler());
    }

    @Test
    void transactionRepository_returnsInMemoryRepo() {
        assertNotNull(config.transactionRepository());
    }

    @Test
    void dailyLimitPolicy_parsesLimit() {
        DailyLimitPolicy policy = config.dailyLimitPolicy("25000");
        assertNotNull(policy);
        assert policy.getPerTransactionLimit().compareTo(new BigDecimal("25000")) == 0;
    }

    @Test
    void transactionService_returnsNonNull() {
        TransactionRepository repo = new InMemoryTransactionRepository();
        AuditLogStore store = new InMemoryAuditLogStore();
        Clock clock = config.clock();
        AuditLogger auditLogger = config.auditLogger(store, clock);
        PIIHandler piiHandler = config.piiHandler();
        DailyLimitPolicy policy = config.dailyLimitPolicy("25000");

        assertNotNull(config.transactionService(repo, auditLogger, piiHandler, policy, clock));
    }
}
