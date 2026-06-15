package com.bofa.balance.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bofa.balance.AccountNotFoundException;
import com.bofa.balance.BalanceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Unit tests for {@link BalanceController}. All data is synthetic. */
@ExtendWith(MockitoExtension.class)
class BalanceControllerTest {

    @Mock
    private BalanceService balanceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BalanceController controller = new BalanceController(balanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getBalance_knownAccount_returns200WithBalance() throws Exception {
        when(balanceService.availableBalance("acct-synth-1"))
                .thenReturn(new BigDecimal("750.00"));

        mockMvc.perform(get("/accounts/acct-synth-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-synth-1"))
                .andExpect(jsonPath("$.availableBalance").value(750.00));
    }

    @Test
    void getBalance_unknownAccount_returns404WithError() throws Exception {
        when(balanceService.availableBalance("acct-unknown"))
                .thenThrow(new AccountNotFoundException("Unknown account: acct-unknown"));

        mockMvc.perform(get("/accounts/acct-unknown/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("account_not_found"))
                .andExpect(jsonPath("$.reason").value("Unknown account: acct-unknown"));
    }

    @Test
    void getBalance_zeroBalance_returns200() throws Exception {
        when(balanceService.availableBalance("acct-synth-2"))
                .thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/accounts/acct-synth-2/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0));
    }

    @Test
    void getBalance_negativeAvailable_returns200() throws Exception {
        when(balanceService.availableBalance("acct-synth-3"))
                .thenReturn(new BigDecimal("-150.00"));

        mockMvc.perform(get("/accounts/acct-synth-3/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(-150.00));
    }
}
