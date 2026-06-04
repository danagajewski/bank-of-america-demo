package com.bofa.balance;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory account store standing in for the core deposit ledger. */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Account save(Account account) {
        accounts.put(account.getAccountId(), account);
        return account;
    }
}
