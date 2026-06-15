package com.bofa.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Account} model. All data is synthetic. */
class AccountTest {

    @Test
    void constructor_setsAllFields() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("5000.00"), new BigDecimal("100.00"));

        assertEquals("acct-synth-1", account.getAccountId());
        assertEquals(new BigDecimal("5000.00"), account.getLedgerBalance());
        assertEquals(new BigDecimal("100.00"), account.getOverdraftLimit());
        assertTrue(account.getHolds().isEmpty());
    }

    @Test
    void setLedgerBalance_updatesValue() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);

        account.setLedgerBalance(new BigDecimal("2500.00"));
        assertEquals(new BigDecimal("2500.00"), account.getLedgerBalance());
    }

    @Test
    void setOverdraftLimit_updatesValue() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);

        account.setOverdraftLimit(new BigDecimal("300.00"));
        assertEquals(new BigDecimal("300.00"), account.getOverdraftLimit());
    }

    @Test
    void addHold_appendsToHoldList() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);

        account.addHold(new Hold("h1", new BigDecimal("100.00"), "auth"));
        account.addHold(new Hold("h2", new BigDecimal("200.00"), "pending"));

        assertEquals(2, account.getHolds().size());
    }

    @Test
    void getHolds_returnsUnmodifiableList() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        account.addHold(new Hold("h1", new BigDecimal("50.00"), "auth"));

        List<Hold> holds = account.getHolds();
        assertThrows(UnsupportedOperationException.class,
                () -> holds.add(new Hold("h-evil", BigDecimal.ONE, "bypass")));
    }

    @Test
    void removeHold_existingHold_returnsTrueAndRemoves() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        account.addHold(new Hold("h1", new BigDecimal("50.00"), "auth"));
        account.addHold(new Hold("h2", new BigDecimal("75.00"), "pending"));

        assertTrue(account.removeHold("h1"));
        assertEquals(1, account.getHolds().size());
        assertEquals("h2", account.getHolds().get(0).getId());
    }

    @Test
    void removeHold_nonexistentHold_returnsFalse() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        account.addHold(new Hold("h1", new BigDecimal("50.00"), "auth"));

        assertFalse(account.removeHold("h-nonexistent"));
        assertEquals(1, account.getHolds().size());
    }

    @Test
    void removeHold_emptyHoldList_returnsFalse() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);

        assertFalse(account.removeHold("h1"));
    }

    @Test
    void removeHold_removeSameHoldTwice_secondReturnsFalse() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        account.addHold(new Hold("h1", new BigDecimal("50.00"), "auth"));

        assertTrue(account.removeHold("h1"));
        assertFalse(account.removeHold("h1"));
    }
}
