package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionRequestTest {

    @Test
    void noArgConstructor_fieldsAreNull() {
        TransactionRequest req = new TransactionRequest();
        assertNull(req.getIdempotencyKey());
        assertNull(req.getSourceAccount());
        assertNull(req.getDestinationAccount());
        assertNull(req.getAmount());
        assertNull(req.getCurrency());
        assertNull(req.getInitiatedBy());
    }

    @Test
    void allArgConstructor_fieldsSet() {
        TransactionRequest req = new TransactionRequest(
                "key", "src", "dst", BigDecimal.TEN, "GBP", "user");
        assertEquals("key", req.getIdempotencyKey());
        assertEquals("src", req.getSourceAccount());
        assertEquals("dst", req.getDestinationAccount());
        assertEquals(BigDecimal.TEN, req.getAmount());
        assertEquals("GBP", req.getCurrency());
        assertEquals("user", req.getInitiatedBy());
    }

    @Test
    void setters_overrideValues() {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey("k");
        req.setSourceAccount("s");
        req.setDestinationAccount("d");
        req.setAmount(BigDecimal.ONE);
        req.setCurrency("JPY");
        req.setInitiatedBy("u");
        assertEquals("k", req.getIdempotencyKey());
        assertEquals("s", req.getSourceAccount());
        assertEquals("d", req.getDestinationAccount());
        assertEquals(BigDecimal.ONE, req.getAmount());
        assertEquals("JPY", req.getCurrency());
        assertEquals("u", req.getInitiatedBy());
    }
}
