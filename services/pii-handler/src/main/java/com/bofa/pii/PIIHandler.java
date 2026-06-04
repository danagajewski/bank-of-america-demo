package com.bofa.pii;

/**
 * Centralized masking of customer PII. Every service that renders, logs, or
 * transmits sensitive identifiers must route through this handler so that raw
 * values (SSNs, card PANs, account numbers) never leak into logs, analytics, or
 * downstream third-party providers.
 *
 * <p>All examples in tests must use synthetic data only.
 */
public final class PIIHandler {

    private static final char MASK = '*';

    /**
     * Mask a US Social Security Number, preserving only the last four digits.
     * Accepts either {@code 123-45-6789} or {@code 123456789} formats and always
     * renders as {@code ***-**-6789}.
     *
     * @throws IllegalArgumentException if the value is null or not 9 digits
     */
    public String maskSSN(String ssn) {
        if (ssn == null) {
            throw new IllegalArgumentException("SSN must not be null");
        }
        String digits = ssn.replaceAll("[^0-9]", "");
        if (digits.length() != 9) {
            throw new IllegalArgumentException("SSN must contain exactly 9 digits");
        }
        String last4 = digits.substring(5);
        return "***-**-" + last4;
    }

    /**
     * Mask an account or card number, preserving only the last four digits.
     *
     * @throws IllegalArgumentException if the value is null or shorter than 5 chars
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            throw new IllegalArgumentException("Account number must not be null");
        }
        String digits = accountNumber.replaceAll("[^0-9]", "");
        if (digits.length() < 5) {
            throw new IllegalArgumentException("Account number is too short to mask safely");
        }
        String last4 = digits.substring(digits.length() - 4);
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < digits.length() - 4; i++) {
            masked.append(MASK);
        }
        masked.append(last4);
        return masked.toString();
    }

    /**
     * Mask an email address, preserving the first character of the local part and
     * the full domain (e.g. {@code j***@example.com}).
     */
    public String maskEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null");
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            throw new IllegalArgumentException("Email is not in a valid format");
        }
        char first = email.charAt(0);
        String domain = email.substring(at);
        return first + "***" + domain;
    }

    /**
     * Return true if the supplied log line still contains the raw secret. Used by
     * services to assert that a value was masked before it was written.
     */
    public boolean containsRaw(String logLine, String rawValue) {
        if (logLine == null || rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        return logLine.contains(rawValue);
    }
}
