package com.bofa.balance.web;

import com.bofa.balance.AccountNotFoundException;
import com.bofa.balance.BalanceService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST surface for balance inquiries. */
@RestController
@RequestMapping("/accounts")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> balance(@PathVariable("accountId") String accountId) {
        BigDecimal available = balanceService.availableBalance(accountId);
        return ResponseEntity.ok(Map.of("accountId", accountId, "availableBalance", available));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "account_not_found", "reason", ex.getMessage()));
    }
}
