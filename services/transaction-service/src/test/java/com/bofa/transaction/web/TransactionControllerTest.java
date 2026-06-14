package com.bofa.transaction.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.bofa.transaction.Transaction;
import com.bofa.transaction.TransactionRequest;
import com.bofa.transaction.TransactionService;
import com.bofa.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock private TransactionService transactionService;
    @InjectMocks private TransactionController controller;

    @Test
    void create_returnsCreatedStatus() {
        TransactionRequest req = new TransactionRequest(
                "key-1", "1234567890", "9876543210",
                new BigDecimal("100"), "USD", "synth-user");
        Transaction tx = new Transaction(
                "tx-1", "key-1", "1234567890", "9876543210",
                new BigDecimal("100"), "USD", TransactionStatus.POSTED, Instant.now());
        when(transactionService.createTransaction(req)).thenReturn(tx);

        ResponseEntity<Transaction> response = controller.create(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(tx, response.getBody());
    }
}
