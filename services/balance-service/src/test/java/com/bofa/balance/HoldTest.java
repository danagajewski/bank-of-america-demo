package com.bofa.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Hold} value object. All data is synthetic. */
class HoldTest {

    @Test
    void constructor_setsAllFields() {
        Hold hold = new Hold("hold-synth-1", new BigDecimal("99.99"), "card auth");

        assertEquals("hold-synth-1", hold.getId());
        assertEquals(new BigDecimal("99.99"), hold.getAmount());
        assertEquals("card auth", hold.getReason());
    }

    @Test
    void constructor_nullReason_isAccepted() {
        Hold hold = new Hold("hold-synth-2", new BigDecimal("50.00"), null);

        assertEquals("hold-synth-2", hold.getId());
        assertEquals(new BigDecimal("50.00"), hold.getAmount());
        assertNull(hold.getReason());
    }

    @Test
    void constructor_zeroAmount_isStored() {
        Hold hold = new Hold("hold-synth-3", BigDecimal.ZERO, "zero hold");
        assertEquals(BigDecimal.ZERO, hold.getAmount());
    }

    @Test
    void constructor_veryLargeAmount_isStored() {
        BigDecimal large = new BigDecimal("99999999999.99");
        Hold hold = new Hold("hold-synth-4", large, "large hold");
        assertEquals(large, hold.getAmount());
    }
}
