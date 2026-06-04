package com.bofa.transaction;

import com.bofa.audit.AuditLogStore;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.InMemoryAuditLogStore;
import com.bofa.pii.PIIHandler;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the transaction collaborators as Spring beans. */
@Configuration
public class TransactionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuditLogStore auditLogStore() {
        return new InMemoryAuditLogStore();
    }

    @Bean
    public AuditLogger auditLogger(AuditLogStore store, Clock clock) {
        return new AuditLogger(store, clock);
    }

    @Bean
    public PIIHandler piiHandler() {
        return new PIIHandler();
    }

    @Bean
    public TransactionRepository transactionRepository() {
        return new InMemoryTransactionRepository();
    }

    @Bean
    public DailyLimitPolicy dailyLimitPolicy(
            @Value("${transaction.per-transaction-limit:25000}") String limit) {
        return new DailyLimitPolicy(new BigDecimal(limit));
    }

    @Bean
    public TransactionService transactionService(TransactionRepository repository,
                                                 AuditLogger auditLogger,
                                                 PIIHandler piiHandler,
                                                 DailyLimitPolicy dailyLimitPolicy,
                                                 Clock clock) {
        return new TransactionService(repository, auditLogger, piiHandler, dailyLimitPolicy, clock);
    }
}
