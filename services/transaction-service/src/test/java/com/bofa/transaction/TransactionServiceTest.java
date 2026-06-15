package com.bofa.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

/**
 * Unit tests for {@link TransactionService#createTransaction(TransactionRequest)}.
 * Covers every validation branch, limit-rejection audit behavior, PII masking,
 * and idempotency replay. Uses synthetic data only.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static final String SOURCE_ACCOUNT = "0001234567";
    private static final String DEST_ACCOUNT = "0009876543";
    private static final String CURRENCY = "USD";
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final String INITIATOR = "customer-42";

    @Mock
    private TransactionRepository repository;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private PIIHandler piiHandler;

    @Mock
    private DailyLimitPolicy dailyLimitPolicy;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, auditLogger, piiHandler, dailyLimitPolicy, FIXED_CLOCK);
    }

    private TransactionRequest validRequest() {
        return new TransactionRequest(
                IDEMPOTENCY_KEY,
                SOURCE_ACCOUNT,
                DEST_ACCOUNT,
                new BigDecimal("250.00"),
                CURRENCY,
                INITIATOR);
    }

    // ─── Validation branches ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation: structural checks")
    class ValidationTests {

        @Test
        @DisplayName("null request throws IllegalArgumentException")
        void nullRequest() {
            assertThatThrownBy(() -> service.createTransaction(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("transaction request must not be null");
        }

        @Test
        @DisplayName("null amount throws IllegalArgumentException")
        void nullAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(null);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must not be null");
        }

        @Test
        @DisplayName("zero amount throws IllegalArgumentException")
        void zeroAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(BigDecimal.ZERO);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be greater than zero");
        }

        @Test
        @DisplayName("negative amount throws IllegalArgumentException")
        void negativeAmount() {
            TransactionRequest req = validRequest();
            req.setAmount(new BigDecimal("-10.00"));

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be greater than zero");
        }

        @Test
        @DisplayName("blank source account throws IllegalArgumentException")
        void blankSourceAccount() {
            TransactionRequest req = validRequest();
            req.setSourceAccount("   ");

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("source account must not be blank");
        }

        @Test
        @DisplayName("null source account throws IllegalArgumentException")
        void nullSourceAccount() {
            TransactionRequest req = validRequest();
            req.setSourceAccount(null);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("source account must not be blank");
        }

        @Test
        @DisplayName("blank destination account throws IllegalArgumentException")
        void blankDestinationAccount() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount("");

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("destination account must not be blank");
        }

        @Test
        @DisplayName("null destination account throws IllegalArgumentException")
        void nullDestinationAccount() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount(null);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("destination account must not be blank");
        }

        @Test
        @DisplayName("source equals destination throws IllegalArgumentException")
        void sourceEqualsDestination() {
            TransactionRequest req = validRequest();
            req.setDestinationAccount(SOURCE_ACCOUNT);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("source and destination accounts must differ");
        }

        @Test
        @DisplayName("blank currency throws IllegalArgumentException")
        void blankCurrency() {
            TransactionRequest req = validRequest();
            req.setCurrency("  ");

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("currency must not be blank");
        }

        @Test
        @DisplayName("null currency throws IllegalArgumentException")
        void nullCurrency() {
            TransactionRequest req = validRequest();
            req.setCurrency(null);

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("currency must not be blank");
        }
    }

    // ─── Business rule: per-transaction limit ───────────────────────────────────

    @Nested
    @DisplayName("Business rule: per-transaction limit rejection")
    class LimitRejectionTests {

        @Test
        @DisplayName("over-limit amount throws TransactionRejectedException and emits DENIED audit event")
        void overLimitRejected() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(true);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> service.createTransaction(req))
                    .isInstanceOf(TransactionRejectedException.class)
                    .hasMessage("Amount exceeds per-transaction limit");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getType()).isEqualTo(AuditEventType.TRANSACTION_REJECTED);
            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(event.getActor()).isEqualTo(INITIATOR);
            assertThat(event.getResourceId()).isEqualTo("******4567");
            assertThat(event.getMessage()).contains("per-transaction limit");
        }

        @Test
        @DisplayName("over-limit rejection masks the account number (never logs raw)")
        void overLimitMasksAccount() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(true);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            try {
                service.createTransaction(req);
            } catch (TransactionRejectedException ignored) {
            }

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getResourceId()).doesNotContain(SOURCE_ACCOUNT);
            assertThat(event.getResourceId()).isEqualTo("******4567");
            assertThat(event.getMessage()).doesNotContain(SOURCE_ACCOUNT);
        }

        @Test
        @DisplayName("over-limit uses 'system' as actor when initiatedBy is null")
        void overLimitNullInitiator() {
            TransactionRequest req = validRequest();
            req.setInitiatedBy(null);
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(true);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            try {
                service.createTransaction(req);
            } catch (TransactionRejectedException ignored) {
            }

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());
            assertThat(captor.getValue().getActor()).isEqualTo("system");
        }
    }

    // ─── Idempotency ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency replay")
    class IdempotencyTests {

        @Test
        @DisplayName("replaying the same idempotency key returns the original booking")
        void idempotencyReplay() {
            Transaction original = new Transaction(
                    "txn-001", IDEMPOTENCY_KEY, SOURCE_ACCOUNT, DEST_ACCOUNT,
                    new BigDecimal("250.00"), CURRENCY, TransactionStatus.POSTED, FIXED_INSTANT);

            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(original));

            TransactionRequest req = validRequest();
            Transaction result = service.createTransaction(req);

            assertThat(result).isSameAs(original);
            verify(repository, never()).save(any());
            verify(auditLogger, never()).log(any(AuditEvent.class));
        }
    }

    // ─── Successful booking ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful transaction creation")
    class SuccessTests {

        @Test
        @DisplayName("valid request creates and persists a POSTED transaction")
        void successfulBooking() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(false);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            Transaction result = service.createTransaction(req);

            assertThat(result).isNotNull();
            assertThat(result.getSourceAccount()).isEqualTo(SOURCE_ACCOUNT);
            assertThat(result.getDestinationAccount()).isEqualTo(DEST_ACCOUNT);
            assertThat(result.getAmount()).isEqualByComparingTo("250.00");
            assertThat(result.getCurrency()).isEqualTo(CURRENCY);
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.POSTED);
            assertThat(result.getCreatedAt()).isEqualTo(FIXED_INSTANT);
            assertThat(result.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("successful booking emits TRANSACTION_CREATED / SUCCESS audit event")
        void successfulBookingAudit() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(false);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTransaction(req);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getType()).isEqualTo(AuditEventType.TRANSACTION_CREATED);
            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(event.getActor()).isEqualTo(INITIATOR);
            assertThat(event.getResourceId()).isEqualTo("******4567");
            assertThat(event.getMessage()).contains("Transaction posted");
        }

        @Test
        @DisplayName("successful booking masks the account number in the audit event")
        void successfulBookingMasksAccount() {
            TransactionRequest req = validRequest();
            when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
            when(dailyLimitPolicy.exceedsLimit(req.getAmount())).thenReturn(false);
            when(piiHandler.maskAccountNumber(SOURCE_ACCOUNT)).thenReturn("******4567");
            when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditLogger.log(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTransaction(req);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditLogger).log(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getResourceId()).doesNotContain(SOURCE_ACCOUNT);
            assertThat(event.getResourceId()).isEqualTo("******4567");
        }
    }
}
