package com.bofa.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive coverage for {@link BalanceService}. Covers available-balance
 * computation, withdrawal eligibility (overdraft boundaries), hold placement
 * and release, and all error/edge-case paths. All data is synthetic.
 */
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountRepository repository;

    private BalanceService service;

    @BeforeEach
    void setUp() {
        service = new BalanceService(repository);
    }

    // ── totalHolds ──────────────────────────────────────────────────────

    @Test
    void totalHolds_noHolds_returnsZero() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertEquals(new BigDecimal("0"), service.totalHolds(account));
    }

    @Test
    void totalHolds_singleHold_returnsHoldAmount() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("250.00"), "card auth"));
        assertEquals(new BigDecimal("250.00"), service.totalHolds(account));
    }

    @Test
    void totalHolds_multipleHolds_returnsSumOfAllHolds() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("100.00"), "card auth"));
        account.addHold(new Hold("h2", new BigDecimal("200.50"), "ach pending"));
        account.addHold(new Hold("h3", new BigDecimal("50.25"), "wire hold"));
        assertEquals(new BigDecimal("350.75"), service.totalHolds(account));
    }

    // ── availableBalance(Account) ───────────────────────────────────────

    @Test
    void availableBalance_nullAccount_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.availableBalance((Account) null));
        assertEquals("account must not be null", ex.getMessage());
    }

    @Test
    void availableBalance_noHolds_returnsLedgerBalance() {
        Account account = syntheticAccount("5000.00", "0.00");
        assertEquals(new BigDecimal("5000.00"), service.availableBalance(account));
    }

    @Test
    void availableBalance_withHolds_subtractsHoldsFromLedger() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("250.00"), "card auth"));
        assertEquals(new BigDecimal("750.00"), service.availableBalance(account));
    }

    @Test
    void availableBalance_holdsExceedLedger_returnsNegative() {
        Account account = syntheticAccount("100.00", "500.00");
        account.addHold(new Hold("h1", new BigDecimal("300.00"), "large hold"));
        assertEquals(new BigDecimal("-200.00"), service.availableBalance(account));
    }

    @Test
    void availableBalance_zeroLedgerNoHolds_returnsZero() {
        Account account = syntheticAccount("0.00", "0.00");
        assertEquals(new BigDecimal("0.00"), service.availableBalance(account));
    }

    // ── availableBalance(String) ────────────────────────────────────────

    @Test
    void availableBalance_byId_knownAccount_returnsBalance() {
        Account account = syntheticAccount("2000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("500.00"), "pending"));
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));

        assertEquals(new BigDecimal("1500.00"), service.availableBalance("acct-synth-1"));
    }

    @Test
    void availableBalance_byId_unknownAccount_throwsAccountNotFound() {
        when(repository.findById("acct-unknown")).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(
                AccountNotFoundException.class,
                () -> service.availableBalance("acct-unknown"));
        assertTrue(ex.getMessage().contains("acct-unknown"));
    }

    @Test
    void availableBalance_byId_nullId_throwsAccountNotFound() {
        when(repository.findById(null)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.availableBalance((String) null));
    }

    @Test
    void availableBalance_byId_emptyString_throwsAccountNotFound() {
        when(repository.findById("")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.availableBalance(""));
    }

    @Test
    void availableBalance_byId_whitespaceOnly_throwsAccountNotFound() {
        when(repository.findById("   ")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.availableBalance("   "));
    }

    // ── canWithdraw ─────────────────────────────────────────────────────

    @Test
    void canWithdraw_nullAmount_throwsIllegalArgument() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertThrows(IllegalArgumentException.class,
                () -> service.canWithdraw(account, null));
    }

    @Test
    void canWithdraw_zeroAmount_throwsIllegalArgument() {
        Account account = syntheticAccount("1000.00", "0.00");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.canWithdraw(account, BigDecimal.ZERO));
        assertEquals("withdrawal amount must be positive", ex.getMessage());
    }

    @Test
    void canWithdraw_negativeAmount_throwsIllegalArgument() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertThrows(IllegalArgumentException.class,
                () -> service.canWithdraw(account, new BigDecimal("-10.00")));
    }

    @Test
    void canWithdraw_amountWithinAvailable_returnsTrue() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertTrue(service.canWithdraw(account, new BigDecimal("500.00")));
    }

    @Test
    void canWithdraw_amountExactlyEqualsAvailable_returnsTrue() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertTrue(service.canWithdraw(account, new BigDecimal("1000.00")));
    }

    @Test
    void canWithdraw_amountOneCentOverAvailable_noOverdraft_returnsFalse() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertFalse(service.canWithdraw(account, new BigDecimal("1000.01")));
    }

    @Test
    void canWithdraw_amountExceedsAvailableButWithinOverdraft_returnsTrue() {
        // available=1000, overdraft=200 → floor=-200 → can withdraw up to 1200
        Account account = syntheticAccount("1000.00", "200.00");
        assertTrue(service.canWithdraw(account, new BigDecimal("1100.00")));
    }

    @Test
    void canWithdraw_amountExactlyAtOverdraftBoundary_returnsTrue() {
        // available=1000, overdraft=200 → floor=-200 → 1000-1200=-200 >= -200 → true
        Account account = syntheticAccount("1000.00", "200.00");
        assertTrue(service.canWithdraw(account, new BigDecimal("1200.00")));
    }

    @Test
    void canWithdraw_amountOneCentOverOverdraftBoundary_returnsFalse() {
        // available=1000, overdraft=200 → floor=-200 → 1000-1200.01=-200.01 < -200 → false
        Account account = syntheticAccount("1000.00", "200.00");
        assertFalse(service.canWithdraw(account, new BigDecimal("1200.01")));
    }

    @Test
    void canWithdraw_withHolds_reducesAvailableBeforeCheck() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("300.00"), "pending"));
        // available = 700
        assertTrue(service.canWithdraw(account, new BigDecimal("700.00")));
        assertFalse(service.canWithdraw(account, new BigDecimal("700.01")));
    }

    @Test
    void canWithdraw_withHoldsAndOverdraft_usesCorrectFloor() {
        Account account = syntheticAccount("1000.00", "100.00");
        account.addHold(new Hold("h1", new BigDecimal("600.00"), "pending"));
        // available = 400, overdraft = 100 → can withdraw up to 500
        assertTrue(service.canWithdraw(account, new BigDecimal("500.00")));
        assertFalse(service.canWithdraw(account, new BigDecimal("500.01")));
    }

    @Test
    void canWithdraw_smallestPossibleAmount_returnsTrue() {
        Account account = syntheticAccount("1000.00", "0.00");
        assertTrue(service.canWithdraw(account, new BigDecimal("0.01")));
    }

    // ── placeHold ───────────────────────────────────────────────────────

    @Test
    void placeHold_nullAmount_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.placeHold("acct-synth-1", null, "test"));
    }

    @Test
    void placeHold_zeroAmount_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.placeHold("acct-synth-1", BigDecimal.ZERO, "test"));
        assertEquals("hold amount must be positive", ex.getMessage());
    }

    @Test
    void placeHold_negativeAmount_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.placeHold("acct-synth-1", new BigDecimal("-5.00"), "test"));
    }

    @Test
    void placeHold_nullAmount_doesNotCallRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> service.placeHold("acct-synth-1", null, "test"));
        verify(repository, never()).findById(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void placeHold_unknownAccount_throwsAccountNotFound() {
        when(repository.findById("acct-unknown")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.placeHold("acct-unknown", new BigDecimal("50.00"), "test"));
    }

    @Test
    void placeHold_unknownAccount_doesNotSave() {
        when(repository.findById("acct-unknown")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.placeHold("acct-unknown", new BigDecimal("50.00"), "test"));
        verify(repository, never()).save(any());
    }

    @Test
    void placeHold_validInput_addsHoldAndSaves() {
        Account account = syntheticAccount("1000.00", "0.00");
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        Hold hold = service.placeHold("acct-synth-1", new BigDecimal("200.00"), "card auth");

        assertNotNull(hold);
        assertNotNull(hold.getId());
        assertEquals(new BigDecimal("200.00"), hold.getAmount());
        assertEquals("card auth", hold.getReason());
        assertEquals(1, account.getHolds().size());
        verify(repository).save(account);
    }

    @Test
    void placeHold_multipleTimes_addsMultipleHolds() {
        Account account = syntheticAccount("1000.00", "0.00");
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        service.placeHold("acct-synth-1", new BigDecimal("100.00"), "hold 1");
        service.placeHold("acct-synth-1", new BigDecimal("200.00"), "hold 2");

        assertEquals(2, account.getHolds().size());
        verify(repository, times(2)).save(account);
    }

    @Test
    void placeHold_eachHoldGetsUniqueId() {
        Account account = syntheticAccount("1000.00", "0.00");
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        Hold h1 = service.placeHold("acct-synth-1", new BigDecimal("100.00"), "a");
        Hold h2 = service.placeHold("acct-synth-1", new BigDecimal("100.00"), "b");

        assertFalse(h1.getId().equals(h2.getId()), "Each hold should have a unique ID");
    }

    @Test
    void placeHold_nullReason_succeeds() {
        Account account = syntheticAccount("1000.00", "0.00");
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        Hold hold = service.placeHold("acct-synth-1", new BigDecimal("50.00"), null);
        assertNotNull(hold);
    }

    @Test
    void placeHold_smallestPossibleAmount_succeeds() {
        Account account = syntheticAccount("1000.00", "0.00");
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        Hold hold = service.placeHold("acct-synth-1", new BigDecimal("0.01"), "micro");
        assertEquals(new BigDecimal("0.01"), hold.getAmount());
        verify(repository).save(account);
    }

    // ── releaseHold ─────────────────────────────────────────────────────

    @Test
    void releaseHold_unknownAccount_throwsAccountNotFound() {
        when(repository.findById("acct-unknown")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> service.releaseHold("acct-unknown", "h1"));
    }

    @Test
    void releaseHold_knownHold_returnsTrueAndSaves() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("100.00"), "test"));
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        assertTrue(service.releaseHold("acct-synth-1", "h1"));
        verify(repository).save(account);
        assertEquals(0, account.getHolds().size());
    }

    @Test
    void releaseHold_unknownHold_returnsFalseAndDoesNotSave() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("100.00"), "test"));
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));

        assertFalse(service.releaseHold("acct-synth-1", "h-nonexistent"));
        verify(repository, never()).save(any());
        assertEquals(1, account.getHolds().size());
    }

    @Test
    void releaseHold_sameHoldTwice_secondCallReturnsFalse() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("100.00"), "test"));
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        assertTrue(service.releaseHold("acct-synth-1", "h1"));
        assertFalse(service.releaseHold("acct-synth-1", "h1"));
        verify(repository, times(1)).save(account);
    }

    @Test
    void releaseHold_releasesCorrectHoldAmongMultiple() {
        Account account = syntheticAccount("1000.00", "0.00");
        account.addHold(new Hold("h1", new BigDecimal("100.00"), "first"));
        account.addHold(new Hold("h2", new BigDecimal("200.00"), "second"));
        account.addHold(new Hold("h3", new BigDecimal("300.00"), "third"));
        when(repository.findById("acct-synth-1")).thenReturn(Optional.of(account));
        when(repository.save(account)).thenReturn(account);

        assertTrue(service.releaseHold("acct-synth-1", "h2"));
        assertEquals(2, account.getHolds().size());
        assertTrue(account.getHolds().stream().noneMatch(h -> h.getId().equals("h2")));
    }

    // ── helper ──────────────────────────────────────────────────────────

    private Account syntheticAccount(String ledgerBalance, String overdraftLimit) {
        return new Account("acct-synth-1",
                new BigDecimal(ledgerBalance), new BigDecimal(overdraftLimit));
    }
}
