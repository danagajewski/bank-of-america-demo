package com.bofa.balance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Computes account balances and enforces withdrawal eligibility. The available
 * balance is the ledger balance minus the sum of outstanding holds; withdrawals
 * may dip into the configured overdraft limit but no further.
 */
public class BalanceService {

    private final AccountRepository repository;

    public BalanceService(AccountRepository repository) {
        this.repository = repository;
    }

    /** Total of all outstanding holds on the account. */
    public BigDecimal totalHolds(Account account) {
        return account.getHolds().stream()
                .map(Hold::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Available balance = ledger balance - outstanding holds. */
    public BigDecimal availableBalance(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        return account.getLedgerBalance().subtract(totalHolds(account));
    }

    /** Look up available balance by account id. */
    public BigDecimal availableBalance(String accountId) {
        Account account = repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Unknown account: " + accountId));
        return availableBalance(account);
    }

    /**
     * Whether a withdrawal of {@code amount} is permitted, taking the overdraft
     * limit into account.
     */
    public boolean canWithdraw(Account account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        BigDecimal floor = account.getOverdraftLimit().negate();
        return availableBalance(account).subtract(amount).compareTo(floor) >= 0;
    }

    /** Place a hold on the account and return the created hold. */
    public Hold placeHold(String accountId, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("hold amount must be positive");
        }
        Account account = repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Unknown account: " + accountId));
        Hold hold = new Hold(UUID.randomUUID().toString(), amount, reason);
        account.addHold(hold);
        repository.save(account);
        return hold;
    }

    /** Release a previously-placed hold. */
    public boolean releaseHold(String accountId, String holdId) {
        Account account = repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Unknown account: " + accountId));
        boolean removed = account.removeHold(holdId);
        if (removed) {
            repository.save(account);
        }
        return removed;
    }
}
