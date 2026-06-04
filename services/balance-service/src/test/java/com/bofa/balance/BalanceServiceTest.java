package com.bofa.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Baseline coverage for the balance service. Only the available-balance
 * calculation is covered today. Withdrawal eligibility (overdraft floor), hold
 * placement/release, and the not-found path are intentionally NOT yet tested.
 */
class BalanceServiceTest {

    private final BalanceService service =
            new BalanceService(new InMemoryAccountRepository());

    @Test
    void availableBalanceSubtractsHolds() {
        Account account = new Account("acct-demo-1",
                new BigDecimal("1000.00"), new BigDecimal("0.00"));
        account.addHold(new Hold("h1", new BigDecimal("250.00"), "card auth"));

        assertEquals(new BigDecimal("750.00"), service.availableBalance(account));
    }
}
