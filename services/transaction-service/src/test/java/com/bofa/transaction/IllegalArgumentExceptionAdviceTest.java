package com.bofa.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IllegalArgumentExceptionAdviceTest {

    private final IllegalArgumentExceptionAdvice advice = new IllegalArgumentExceptionAdvice();

    @Test
    void handleValidation_returnsBadRequest() {
        ResponseEntity<Map<String, String>> response =
                advice.handleValidation(new IllegalArgumentException("bad field"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_request", response.getBody().get("error"));
        assertEquals("bad field", response.getBody().get("reason"));
    }

    @Test
    void handleRejected_returnsUnprocessableEntity() {
        ResponseEntity<Map<String, String>> response =
                advice.handleRejected(new TransactionRejectedException("limit exceeded"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("transaction_rejected", response.getBody().get("error"));
        assertEquals("limit exceeded", response.getBody().get("reason"));
    }
}
