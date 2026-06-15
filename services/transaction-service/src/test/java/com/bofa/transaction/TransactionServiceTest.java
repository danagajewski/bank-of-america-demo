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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static final String SOURCE_ACCOUNT = "0001234567";
    private static final String DEST_ACCOUNT = "0009876543";
    private static final String MASKED_SOURCE = "******4567";
    private static final BigDecimal AMOUNT = new BigDecimal("250.00");
    private static final BigDecimal OVER_LIMIT_AMOUNT = new BigDecimal("50000.01");
    private static final BigDecimal PER_TXN_LIMIT = new BigDecimal("50000.00");

    @Mock private TransactionRepository repository;
    @Mock private AuditLogger auditLogger;
    @Mock private PIIHandler piiHandler;

    private DailyLimitPolicy dailyLimitPolicy;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        dailyLimitPolicy = new DailyLimitPolicy(PER_TXN_LIMIT);
        service = new TransactionService(repository, auditLogger, piiHandler, dailyLimitPolicy, FIXED_CLOCK);
    }

    private TransactionRequest validRequest() {
        return new TransactionRequest("idem-001", SOURCE_ACCOUNT, DEST_ACCOUNT, AMOUNT, "USD", "teller-42");
    }

    // ── Validation branch tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Validation: structural checks")
    class ValidationTests {

        @Test
        @DisplayName("null request throws IllegalArgumentException")
        void nullRequest() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(null));
            assertEquals("transaction request must not be null", ex.getMessage());
        }

        @Test
        @DisplayName("null amount throws IllegalArgumentException")
        void nullAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("amount must not be null", ex.getMessage());
        }

        @Test
        @DisplayName("zero amount throws IllegalArgumentException")
        void zeroAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(BigDecimal.ZERO);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("amount must be greater than zero", ex.getMessage());
        }

        @Test
        @DisplayName("negative amount throws IllegalArgumentException")
        void negativeAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(new BigDecimal("-10.00"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("amount must be greater than zero", ex.getMessage());
        }

        @Test
        @DisplayName("blank source account throws IllegalArgumentException")
        void blankSourceAccount() {
            TransactionRequest req = validRequest();
            req.setSourceAccount("   ");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("source account must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("null source account throws IllegalArgumentException")
        void nullSourceAccount() {
            TransactionRequest req = validRequest();
            req.setSourceAccount(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("source account must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("blank destination account throws IllegalArgumentException")
        void blankDestinationAccount() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount("");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("destination account must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("null destination account throws IllegalArgumentException")
        void nullDestinationAccount() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("destination account must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("source equals destination throws IllegalArgumentException")
        void sourceEqualsDestination() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount(SOURCE_ACCOUNT);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("source and destination accounts must differ", ex.getMessage());
        }

        @Test
        @DisplayName("blank currency throws IllegalArgumentException")
        void blankCurrency() {
            TransactionRequest req = validRequest();
            req.setCurrency("  ");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("currency must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("null currency throws IllegalArgumentException")
        void nullCurrency() {
            TransactionRequest req = validRequest();
            req.setCurrency(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createTransaction(req));
            assertEquals("currency must not be blank", ex.getMessage());
        }

        @Test
        @DisplayName("validation failures never touch the repository")
        void validationFailuresNeverPersist() {
            assertThrows(IllegalArgumentException.class, () -> service.createTransaction(null));
            verify(repository, never()).save(any());
            verify(repository, never()).findByIdempotencyKey(any());
        }
    }

    // ── Per-transaction limit rejection ──────────────────────────────────

    @Nested
    @DisplayName("Business rule: per-transaction limit")
    class LimitRejectionTests {

        @Test
        @DisplayName("amount exceeding limit throws TransactionRejectedException")
        void exceedsLimit() {
            TransactionRequest req = validRequest();
            req.setAmount(OVER_LIMIT_AMOUNT);
            when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);

            TransactionRejectedException ex = assertThrows(TransactionRejectedException.class,
                    () -> service.createTransaction(req));
            assertEquals("Amount exceeds per-transaction limit", ex.getMessage());
        }

        @Test
        @DisplayName("over-limit rejection emits TRANSACTION_REJECTED / DENIED audit event")
        void overLimitAuditEvent() {
            TransactionRequest req = validRequest();
            req.setAmount(OVER_LIMIT_AMOUNT);
            when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);

            assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            AuditEvent event = captor.getValue();
            assertEquals(AuditEventType.TRANSACTION_REJECTED, event.getType());
            assertEquals(AuditOutcome.DENIED, event.getOutcome());
        }

        @Test
        @DisplayName("over-limit audit event uses masked account number, not raw")
        void overLimitAuditMasksAccount() {
            TransactionRequest req = validRequest();
            req.setAmount(OVER_LIMIT_AMOUNT);
            when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);

            assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));

            verify(piiHandler).maskAccountNumber(SOURCE_ACCOUNT);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            AuditEvent event = captor.getValue();
            assertEquals(MASKED_SOURCE, event.getResourceId());
        }

        @Test
        @DisplayName("over-limit rejection never persists a transaction")
        void overLimitNeverPersists() {
            TransactionRequest req = validRequest();
            req.setAmount(OVER_LIMIT_AMOUNT);
            when(repository.findByIdempotencyKey(req.getIdempotencyKey())).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);

            assertThrows(TransactionRejectedException.class, () -> service.createTransaction(req));
            verify(repository, never()).save(any());
        }
    }

    // ── Idempotency replay ───────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency replay")
    class IdempotencyTests {

        @Test
        @DisplayName("replaying the same key returns the original booking")
        void idempotencyReplay() {
            TransactionRequest req = validRequest();
            Transaction original = new Transaction("txn-abc", "idem-001", SOURCE_ACCOUNT,
                    DEST_ACCOUNT, AMOUNT, "USD", TransactionStatus.POSTED, FIXED_INSTANT);
            when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(original));

            Transaction result = service.createTransaction(req);

            assertSame(original, result);
            verify(repository, never()).save(any());
            verify(auditLogger, never()).log(any());
        }
    }

    // ── Happy-path booking ───────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path: successful transaction booking")
    class HappyPathTests {

        @Test
        @DisplayName("valid request creates and persists a POSTED transaction")
        void validRequestCreatesTransaction() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            Transaction result = service.createTransaction(req);

            assertNotNull(result.getId());
            assertEquals("idem-001", result.getIdempotencyKey());
            assertEquals(SOURCE_ACCOUNT, result.getSourceAccount());
            assertEquals(DEST_ACCOUNT, result.getDestinationAccount());
            assertEquals(AMOUNT, result.getAmount());
            assertEquals("USD", result.getCurrency());
            assertEquals(TransactionStatus.POSTED, result.getStatus());
            assertEquals(FIXED_INSTANT, result.getCreatedAt());
        }

        @Test
        @DisplayName("successful booking emits TRANSACTION_CREATED / SUCCESS audit event")
        void successAuditEvent() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTransaction(req);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            AuditEvent event = captor.getValue();
            assertEquals(AuditEventType.TRANSACTION_CREATED, event.getType());
            assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
            assertEquals("teller-42", event.getActor());
        }

        @Test
        @DisplayName("successful booking masks account number in audit event")
        void successAuditMasksAccount() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTransaction(req);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            assertEquals(MASKED_SOURCE, captor.getValue().getResourceId());
        }

        @Test
        @DisplayName("null initiatedBy defaults actor to 'system'")
        void nullInitiatedByDefaultsToSystem() {
            TransactionRequest req = validRequest();
            req.setInitiatedBy(null);
            when(repository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn(MASKED_SOURCE);
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTransaction(req);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            assertEquals("system", captor.getValue().getActor());
        }
    }
}
