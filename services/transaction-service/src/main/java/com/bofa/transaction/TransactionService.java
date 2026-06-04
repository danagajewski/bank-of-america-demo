package com.bofa.transaction;

import com.bofa.audit.AuditEvent;
import com.bofa.audit.AuditEventType;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.AuditOutcome;
import com.bofa.pii.PIIHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates and books customer money-movement transactions. This is the most
 * compliance-critical path in the platform: every rejection must be deterministic,
 * every booked transaction must be audited, and account numbers must never be
 * written to logs in the clear.
 *
 * <p>Validation runs to completion <em>before</em> any persistence so that a
 * rejected request never leaves a partial transaction behind.
 */
public class TransactionService {

    private final TransactionRepository repository;
    private final AuditLogger auditLogger;
    private final PIIHandler piiHandler;
    private final DailyLimitPolicy dailyLimitPolicy;
    private final Clock clock;

    public TransactionService(TransactionRepository repository,
                              AuditLogger auditLogger,
                              PIIHandler piiHandler,
                              DailyLimitPolicy dailyLimitPolicy,
                              Clock clock) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.piiHandler = piiHandler;
        this.dailyLimitPolicy = dailyLimitPolicy;
        this.clock = clock;
    }

    /**
     * Validate and book a transaction.
     *
     * @throws IllegalArgumentException     if the request fails structural validation
     *                                      (null request, null/non-positive amount,
     *                                      missing accounts or currency)
     * @throws TransactionRejectedException if the request violates a business rule
     *                                      (per-transaction limit)
     */
    public Transaction createTransaction(TransactionRequest request) {
        validate(request);

        // Idempotency: replaying the same key returns the original booking.
        Optional<Transaction> existing =
                repository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        if (dailyLimitPolicy.exceedsLimit(request.getAmount())) {
            auditLogger.log(AuditEvent.builder()
                    .type(AuditEventType.TRANSACTION_REJECTED)
                    .outcome(AuditOutcome.DENIED)
                    .actor(actorOf(request))
                    .resourceId(piiHandler.maskAccountNumber(request.getSourceAccount()))
                    .message("Amount exceeds per-transaction limit")
                    .build());
            throw new TransactionRejectedException("Amount exceeds per-transaction limit");
        }

        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(),
                request.getIdempotencyKey(),
                request.getSourceAccount(),
                request.getDestinationAccount(),
                request.getAmount(),
                request.getCurrency(),
                TransactionStatus.POSTED,
                clock.instant());

        Transaction saved = repository.save(transaction);

        auditLogger.log(AuditEvent.builder()
                .type(AuditEventType.TRANSACTION_CREATED)
                .outcome(AuditOutcome.SUCCESS)
                .actor(actorOf(request))
                .resourceId(piiHandler.maskAccountNumber(request.getSourceAccount()))
                .message("Transaction posted: " + saved.getId())
                .build());

        return saved;
    }

    private void validate(TransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("transaction request must not be null");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (isBlank(request.getSourceAccount())) {
            throw new IllegalArgumentException("source account must not be blank");
        }
        if (isBlank(request.getDestinationAccount())) {
            throw new IllegalArgumentException("destination account must not be blank");
        }
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            throw new IllegalArgumentException("source and destination accounts must differ");
        }
        if (isBlank(request.getCurrency())) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    private String actorOf(TransactionRequest request) {
        return request.getInitiatedBy() == null ? "system" : request.getInitiatedBy();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
