package com.bofa.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A customer deposit account with a ledger balance and outstanding holds. */
public final class Account {

    private final String accountId;
    private BigDecimal ledgerBalance;
    private BigDecimal overdraftLimit;
    private final List<Hold> holds = new ArrayList<>();

    public Account(String accountId, BigDecimal ledgerBalance, BigDecimal overdraftLimit) {
        this.accountId = accountId;
        this.ledgerBalance = ledgerBalance;
        this.overdraftLimit = overdraftLimit;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(BigDecimal overdraftLimit) {
        this.overdraftLimit = overdraftLimit;
    }

    public List<Hold> getHolds() {
        return Collections.unmodifiableList(holds);
    }

    public void addHold(Hold hold) {
        holds.add(hold);
    }

    public boolean removeHold(String holdId) {
        return holds.removeIf(h -> h.getId().equals(holdId));
    }
}
