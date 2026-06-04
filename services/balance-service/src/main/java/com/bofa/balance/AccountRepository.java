package com.bofa.balance;

import java.util.Optional;

/** Persistence boundary for customer accounts. */
public interface AccountRepository {

    Optional<Account> findById(String accountId);

    Account save(Account account);
}
