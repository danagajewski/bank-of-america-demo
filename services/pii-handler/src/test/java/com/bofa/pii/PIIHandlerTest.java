package com.bofa.pii;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Baseline coverage for PII masking. Only account-number masking is covered today.
 * SSN masking and the "raw value never logged" guarantee are intentionally NOT
 * yet tested -- these are the compliance-critical paths an examiner will ask about.
 *
 * <p>Synthetic data only.
 */
class PIIHandlerTest {

    private final PIIHandler handler = new PIIHandler();

    @Test
    void masksAccountNumberPreservingLastFour() {
        // Synthetic account number.
        assertEquals("********9012", handler.maskAccountNumber("1234-5678-9012"));
    }
}
