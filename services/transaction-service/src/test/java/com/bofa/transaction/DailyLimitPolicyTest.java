package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DailyLimitPolicyTest {

    private final BigDecimal limit = new BigDecimal("25000");
    private final DailyLimitPolicy policy = new DailyLimitPolicy(limit);

    @Test
    void amountBelowLimit_doesNotExceed() {
        assertFalse(policy.exceedsLimit(new BigDecimal("24999.99")));
    }

    @Test
    void amountExactlyAtLimit_doesNotExceed() {
        assertFalse(policy.exceedsLimit(new BigDecimal("25000")));
    }

    @Test
    void amountOneCentOverLimit_exceeds() {
        assertTrue(policy.exceedsLimit(new BigDecimal("25000.01")));
    }

    @Test
    void amountWellOverLimit_exceeds() {
        assertTrue(policy.exceedsLimit(new BigDecimal("100000")));
    }

    @Test
    void getPerTransactionLimit_returnsConfiguredValue() {
        assertEquals(limit, policy.getPerTransactionLimit());
    }
}
