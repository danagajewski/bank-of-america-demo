package com.bofa.balance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the balance collaborators as Spring beans. */
@Configuration
public class BalanceConfig {

    @Bean
    public AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }

    @Bean
    public BalanceService balanceService(AccountRepository repository) {
        return new BalanceService(repository);
    }
}
