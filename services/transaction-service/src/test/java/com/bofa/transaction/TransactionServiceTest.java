package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
 * Comprehensive coverage for {@link TransactionService#createTransaction}.
 * All data is synthetic.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-06-15T12:00:00Z");
    private static final BigDecimal LIMIT = new BigDecimal("25000");

    @Mock private TransactionRepository repository;
    @Mock private AuditLogger auditLogger;

    private PIIHandler piiHandler;
    private DailyLimitPolicy dailyLimitPolicy;
    private Clock clock;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        piiHandler = new PIIHandler();
        dailyLimitPolicy = new DailyLimitPolicy(LIMIT);
        service = new TransactionService(repository, auditLogger, piiHandler, dailyLimitPolicy, clock);
    }

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                "idem-001", "1234567890", "9876543210",
                new BigDecimal("100.00"), "USD", "synth-user");
    }

    // ---------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------

    @Test
    void createTransaction_happyPath_postsAndAudits() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.createTransaction(req);

        assertNotNull(tx.getId());
        assertEquals("idem-001", tx.getIdempotencyKey());
        assertEquals("1234567890", tx.getSourceAccount());
        assertEquals("9876543210", tx.getDestinationAccount());
        assertEquals(new BigDecimal("100.00"), tx.getAmount());
        assertEquals("USD", tx.getCurrency());
        assertEquals(TransactionStatus.POSTED, tx.getStatus());
        assertEquals(FIXED_NOW, tx.getCreatedAt());

        verify(repository).save(any(Transaction.class));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        AuditEvent evt = cap.getValue();
        assertEquals(AuditEventType.TRANSACTION_CREATED, evt.getType());
        assertEquals(AuditOutcome.SUCCESS, evt.getOutcome());
        assertEquals("synth-user", evt.getActor());
    }

    // ---------------------------------------------------------------
    // Null / missing required inputs
    // ---------------------------------------------------------------

    @Test
    void createTransaction_nullRequest_throwsIllegalArgument() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(null));
        assertEquals("transaction request must not be null", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_nullAmount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("amount must not be null", ex.getMessage());
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Numeric boundary: amount zero and negative
    // ---------------------------------------------------------------

    @Test
    void createTransaction_amountExactlyZero_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(BigDecimal.ZERO);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("amount must be greater than zero", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_negativeAmount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("-0.01"));
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("amount must be greater than zero", ex.getMessage());
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Blank vs whitespace-only strings
    // ---------------------------------------------------------------

    @Test
    void createTransaction_nullSourceAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("source account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_emptySourceAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount("");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("source account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_whitespaceOnlySourceAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount("   ");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("source account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_nullDestinationAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("destination account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_emptyDestinationAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount("");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("destination account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_whitespaceOnlyDestinationAccount_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setDestinationAccount("   ");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("destination account must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_sourceEqualsDestination_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setSourceAccount("1234567890");
        req.setDestinationAccount("1234567890");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("source and destination accounts must differ", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_nullCurrency_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setCurrency(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("currency must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_emptyCurrency_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setCurrency("");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("currency must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_whitespaceOnlyCurrency_throwsIllegalArgument() {
        TransactionRequest req = validRequest();
        req.setCurrency("   ");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));
        assertEquals("currency must not be blank", ex.getMessage());
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Per-transaction limit: both sides of the boundary
    // ---------------------------------------------------------------

    @Test
    void createTransaction_amountExactlyAtLimit_accepted() {
        TransactionRequest req = validRequest();
        req.setAmount(LIMIT); // 25000 — at the limit
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.createTransaction(req);

        assertEquals(TransactionStatus.POSTED, tx.getStatus());
        verify(repository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_amountOneCentOverLimit_rejected() {
        TransactionRequest req = validRequest();
        req.setAmount(LIMIT.add(new BigDecimal("0.01"))); // 25000.01
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());

        TransactionRejectedException ex =
                assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));
        assertEquals("Amount exceeds per-transaction limit", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_amountOverLimit_auditsRejection() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("50000"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());

        assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        AuditEvent evt = cap.getValue();
        assertEquals(AuditEventType.TRANSACTION_REJECTED, evt.getType());
        assertEquals(AuditOutcome.DENIED, evt.getOutcome());
    }

    // ---------------------------------------------------------------
    // Idempotency replay
    // ---------------------------------------------------------------

    @Test
    void createTransaction_idempotencyReplay_returnsOriginalWithoutSaving() {
        TransactionRequest req = validRequest();
        Transaction existing = new Transaction(
                "existing-id", "idem-001", "1234567890", "9876543210",
                new BigDecimal("100.00"), "USD", TransactionStatus.POSTED, FIXED_NOW);
        when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(existing));

        Transaction result = service.createTransaction(req);

        assertSame(existing, result);
        verify(repository, never()).save(any());
        verify(auditLogger, never()).log(any());
    }

    @Test
    void createTransaction_twoCallsSameKey_savesExactlyOnce() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey("idem-001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new Transaction(
                        "tx-1", "idem-001", "1234567890", "9876543210",
                        new BigDecimal("100.00"), "USD", TransactionStatus.POSTED, FIXED_NOW)));
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createTransaction(req);
        service.createTransaction(req);

        verify(repository, times(1)).save(any(Transaction.class));
    }

    // ---------------------------------------------------------------
    // No partial state on validation failure
    // ---------------------------------------------------------------

    @Test
    void createTransaction_validationFailure_noPersistence() {
        TransactionRequest req = validRequest();
        req.setAmount(null);

        assertThrows(IllegalArgumentException.class, () -> service.createTransaction(req));

        verify(repository, never()).save(any());
        verify(repository, never()).findByIdempotencyKey(any());
        verify(auditLogger, never()).log(any());
    }

    // ---------------------------------------------------------------
    // PII masking in audit events
    // ---------------------------------------------------------------

    @Test
    void createTransaction_success_auditsWithMaskedAccount() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        String resourceId = cap.getValue().getResourceId();
        // The raw account "1234567890" must NOT appear; PIIHandler masks to "******7890"
        assertEquals("******7890", resourceId);
    }

    @Test
    void createTransaction_overLimit_auditsWithMaskedAccount() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("99999"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());

        assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        String resourceId = cap.getValue().getResourceId();
        assertEquals("******7890", resourceId);
    }

    // ---------------------------------------------------------------
    // initiatedBy == null audited as "system"
    // ---------------------------------------------------------------

    @Test
    void createTransaction_nullInitiatedBy_auditActorIsSystem() {
        TransactionRequest req = validRequest();
        req.setInitiatedBy(null);
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        assertEquals("system", cap.getValue().getActor());
    }

    @Test
    void createTransaction_explicitInitiatedBy_auditActorIsSet() {
        TransactionRequest req = validRequest();
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createTransaction(req);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        assertEquals("synth-user", cap.getValue().getActor());
    }

    // ---------------------------------------------------------------
    // Over-limit with null initiatedBy → actor "system" in rejection audit
    // ---------------------------------------------------------------

    @Test
    void createTransaction_overLimitNullInitiatedBy_auditActorIsSystem() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("99999"));
        req.setInitiatedBy(null);
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());

        assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(cap.capture());
        assertEquals("system", cap.getValue().getActor());
    }

    // ---------------------------------------------------------------
    // Small positive amount just above zero
    // ---------------------------------------------------------------

    @Test
    void createTransaction_amountOneCent_accepted() {
        TransactionRequest req = validRequest();
        req.setAmount(new BigDecimal("0.01"));
        when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.createTransaction(req);
        assertEquals(TransactionStatus.POSTED, tx.getStatus());
    }
}
