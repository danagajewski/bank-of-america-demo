package com.bofa.transaction;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps transaction validation and business-rule failures to HTTP responses. */
@RestControllerAdvice
public class IllegalArgumentExceptionAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_request", "reason", ex.getMessage()));
    }

    @ExceptionHandler(TransactionRejectedException.class)
    public ResponseEntity<Map<String, String>> handleRejected(TransactionRejectedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "transaction_rejected", "reason", ex.getMessage()));
    }
}
