package com.bofa.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InMemoryAccountRepository}. All data is synthetic. */
class InMemoryAccountRepositoryTest {

    private InMemoryAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
    }

    @Test
    void findById_emptyRepository_returnsEmpty() {
        Optional<Account> result = repository.findById("acct-synth-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void findById_afterSave_returnsAccount() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        repository.save(account);

        Optional<Account> result = repository.findById("acct-synth-1");
        assertTrue(result.isPresent());
        assertSame(account, result.get());
    }

    @Test
    void findById_differentId_returnsEmpty() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        repository.save(account);

        assertTrue(repository.findById("acct-synth-other").isEmpty());
    }

    @Test
    void save_returnsTheSameAccount() {
        Account account = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        Account returned = repository.save(account);
        assertSame(account, returned);
    }

    @Test
    void save_overwritesPreviousEntry() {
        Account original = new Account("acct-synth-1",
                new BigDecimal("1000.00"), BigDecimal.ZERO);
        repository.save(original);

        Account updated = new Account("acct-synth-1",
                new BigDecimal("2000.00"), new BigDecimal("100.00"));
        repository.save(updated);

        Account found = repository.findById("acct-synth-1").orElseThrow();
        assertEquals(new BigDecimal("2000.00"), found.getLedgerBalance());
        assertSame(updated, found);
    }

    @Test
    void save_multipleAccounts_retrieveEachById() {
        Account a1 = new Account("acct-synth-1",
                new BigDecimal("100.00"), BigDecimal.ZERO);
        Account a2 = new Account("acct-synth-2",
                new BigDecimal("200.00"), BigDecimal.ZERO);
        repository.save(a1);
        repository.save(a2);

        assertSame(a1, repository.findById("acct-synth-1").orElseThrow());
        assertSame(a2, repository.findById("acct-synth-2").orElseThrow());
    }

    @Test
    void findById_nullId_returnsEmpty() {
        assertTrue(repository.findById(null).isEmpty());
    }
}
