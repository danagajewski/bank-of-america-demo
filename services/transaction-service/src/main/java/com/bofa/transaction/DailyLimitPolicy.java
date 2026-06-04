package com.bofa.transaction;

import java.math.BigDecimal;

/** Enforces a per-transaction ceiling used for retail money movement. */
public class DailyLimitPolicy {

    private final BigDecimal perTransactionLimit;

    public DailyLimitPolicy(BigDecimal perTransactionLimit) {
        this.perTransactionLimit = perTransactionLimit;
    }

    public boolean exceedsLimit(BigDecimal amount) {
        return amount.compareTo(perTransactionLimit) >= 0;
    }

    public BigDecimal getPerTransactionLimit() {
        return perTransactionLimit;
    }
}
