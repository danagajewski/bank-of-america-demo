package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bofa.audit.AuditEvent;
import com.bofa.audit.AuditEventType;
import com.bofa.audit.AuditLogger;
import com.bofa.audit.AuditOutcome;
import com.bofa.pii.PIIHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TransactionService#createTransaction(TransactionRequest)}.
 * Covers every validation branch plus the compliance-critical audit and PII paths.
 *
 * <p>Synthetic data only (account 0001234567).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-06-15T12:00:00Z");
    private static final String SRC_ACCOUNT = "0001234567";
    private static final String DST_ACCOUNT = "0009876543";
    private static final String MASKED_SRC = "******4567";

    @Mock private TransactionRepository repository;
    @Mock private AuditLogger auditLogger;
    @Mock private PIIHandler piiHandler;

    private DailyLimitPolicy dailyLimitPolicy;
    private Clock clock;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        dailyLimitPolicy = new DailyLimitPolicy(new BigDecimal("10000"));
        service = new TransactionService(repository, auditLogger, piiHandler, dailyLimitPolicy, clock);
    }

    // --- Validation branches ---

    @Test
    void nullRequest_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(null));
        assertEquals("transaction request must not be null", ex.getMessage());
    }

    @Test
    void nullAmount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("amount must not be null", ex.getMessage());
    }

    @Test
    void zeroAmount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(BigDecimal.ZERO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("amount must be greater than zero", ex.getMessage());
    }

    @Test
    void negativeAmount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("-1"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("amount must be greater than zero", ex.getMessage());
    }

    @Test
    void blankSourceAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount("  ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("source account must not be blank", ex.getMessage());
    }

    @Test
    void nullSourceAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("source account must not be blank", ex.getMessage());
    }

    @Test
    void blankDestinationAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("destination account must not be blank", ex.getMessage());
    }

    @Test
    void nullDestinationAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("destination account must not be blank", ex.getMessage());
    }

    @Test
    void sourceEqualsDestination_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount(SRC_ACCOUNT);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("source and destination accounts must differ", ex.getMessage());
    }

    @Test
    void blankCurrency_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setCurrency("  ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("currency must not be blank", ex.getMessage());
    }

    @Test
    void nullCurrency_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setCurrency(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(req));
        assertEquals("currency must not be blank", ex.getMessage());
    }

    @Test
    void validationFailure_neverPersistsOrAudits() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createTransaction(null));

        verify(repository, never()).save(any());
        verify(auditLogger, never()).log(any());
    }

    // --- Per-transaction limit rejection ---

    @Test
    void exceedsPerTransactionLimit_throwsRejected() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("10001"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);

        assertThrows(TransactionRejectedException.class,
                () -> service.createTransaction(req));
    }

    @Test
    void exceedsLimit_emitsRejectedDeniedAuditEvent() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("10001"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);

        assertThrows(TransactionRejectedException.class,
                () -> service.createTransaction(req));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEventType.TRANSACTION_REJECTED, event.getType());
        assertEquals(AuditOutcome.DENIED, event.getOutcome());
    }

    @Test
    void exceedsLimit_auditEventUsesmaskedAccount() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("10001"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);

        assertThrows(TransactionRejectedException.class,
                () -> service.createTransaction(req));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(MASKED_SRC, event.getResourceId());
    }

    @Test
    void exceedsLimit_neverPersistsTransaction() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("10001"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);

        assertThrows(TransactionRejectedException.class,
                () -> service.createTransaction(req));

        verify(repository, never()).save(any());
    }

    // --- Idempotency replay ---

    @Test
    void duplicateIdempotencyKey_returnsOriginalTransaction() {
        TransactionRequest req = validRequest();
        Transaction original = new Transaction(
                "txn-1", req.getIdempotencyKey(), SRC_ACCOUNT, DST_ACCOUNT,
                new BigDecimal("100"), "USD", TransactionStatus.POSTED, FIXED_NOW);
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.of(original));

        Transaction result = service.createTransaction(req);

        assertSame(original, result);
        verify(repository, never()).save(any());
        verify(auditLogger, never()).log(any());
    }

    // --- Happy path ---

    @Test
    void validRequest_persistsAndReturnsTransaction() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = service.createTransaction(req);

        assertNotNull(result.getId());
        assertEquals(SRC_ACCOUNT, result.getSourceAccount());
        assertEquals(DST_ACCOUNT, result.getDestinationAccount());
        assertEquals(new BigDecimal("100"), result.getAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals(TransactionStatus.POSTED, result.getStatus());
        assertEquals(FIXED_NOW, result.getCreatedAt());
    }

    @Test
    void validRequest_emitsTransactionCreatedAudit() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditEventType.TRANSACTION_CREATED, event.getType());
        assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    }

    @Test
    void validRequest_auditUsesmaskedAccount_neverRaw() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createTransaction(req);

        verify(auditLogger).log(argThat(event ->
                MASKED_SRC.equals(event.getResourceId())
                        && !event.getResourceId().contains(SRC_ACCOUNT)));
    }

    @Test
    void validRequest_actorDefaultsToSystemWhenNull() {
        TransactionRequest req = validRequest();
        req.setInitiatedBy(null);
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        assertEquals("system", captor.getValue().getActor());
    }

    @Test
    void validRequest_actorUsesInitiatedByWhenProvided() {
        TransactionRequest req = validRequest();
        req.setInitiatedBy("customer-42");
        when(repository.findByIdempotencyKey(req.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(piiHandler.maskAccountNumber(SRC_ACCOUNT)).thenReturn(MASKED_SRC);
        when(repository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        assertEquals("customer-42", captor.getValue().getActor());
    }

    // --- Helpers ---

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                "idem-key-001", SRC_ACCOUNT, DST_ACCOUNT,
                new BigDecimal("100"), "USD", "customer-42");
    }
}
