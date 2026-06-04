package com.bofa.balance;

import java.math.BigDecimal;

/** A temporary hold (e.g. a pending card authorization) against an account. */
public final class Hold {

    private final String id;
    private final BigDecimal amount;
    private final String reason;

    public Hold(String id, BigDecimal amount, String reason) {
        this.id = id;
        this.amount = amount;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }
}
