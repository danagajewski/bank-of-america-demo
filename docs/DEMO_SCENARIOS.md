# Demo scenarios: "a test catches a real bug, then Devin fixes the code"

This branch intentionally contains **three planted defects** in compliance-critical
code paths. Each defect lives in a branch that the *existing* test suite does not
exercise, so **CI is green even though the code is wrong**. That is the whole
point: coverage gaps hide real defects.

In the live demo, Devin (driven by the
[`improve-compliance-test-coverage`](../playbooks/improve-compliance-test-coverage.md)
playbook) writes an edge-case test for one of these paths. The test **fails**,
revealing the bug. Devin then diagnoses and **fixes the production code** (not the
test), re-runs, and CI goes green.

> ⚠️ This is a demo fixture branch. The planted bugs must **not** be merged into
> `main`. Keep this branch open for rehearsals/demos only.

---

## Scenario A — PII: SSN masking leaks an extra digit

- **Path:** PII handling (`pii-handler`)
- **File:** `services/pii-handler/src/main/java/com/bofa/pii/PIIHandler.java`
- **Defect:** `maskSSN` uses `digits.substring(4)` instead of `substring(5)`, so it
  preserves the last **five** digits, not four.
- **Observed:** `maskSSN("123-45-6789")` returns `***-**-56789` (should be `***-**-6789`).
- **Why CI is green:** `PIIHandlerTest` only covers `maskAccountNumber`; there is no
  `maskSSN` test today.
- **Edge test to write live:**
  ```java
  assertEquals("***-**-6789", handler.maskSSN("123-45-6789"));
  ```
- **The fix:** `substring(4)` → `substring(5)`.
- **Why it lands:** a PII leak in masking is exactly what the Security Engineer and an
  OCC examiner care about — masked output that still exposes customer data.

## Scenario B — Transaction processing: per-transaction limit off-by-one

- **Path:** Transaction processing (`transaction-service`)
- **File:** `services/transaction-service/src/main/java/com/bofa/transaction/DailyLimitPolicy.java`
- **Defect:** `exceedsLimit` uses `compareTo(limit) >= 0` instead of `> 0`, so a
  transaction **exactly at** the $25,000 ceiling is wrongly rejected.
- **Observed (runtime):** `POST /transactions` with `amount = 25000.00` → `422
  transaction_rejected`; `24999.99` → `201`. The limit is the maximum *allowed*
  amount, so `25000.00` should be `201`.
- **Why CI is green:** `transaction-service` has **no test infrastructure at all** —
  this doubles as the "scaffold tests from scratch" beat. The first boundary test the
  team writes uncovers the bug.
- **Edge test to write live (after scaffolding JUnit):**
  ```java
  assertFalse(policy.exceedsLimit(new BigDecimal("25000.00"))); // at-limit is allowed
  assertTrue(policy.exceedsLimit(new BigDecimal("25000.01")));  // over-limit is rejected
  ```
- **The fix:** `>= 0` → `> 0`.

## Scenario C — Audit logging: failed-auth severity misclassified

- **Path:** Audit logging (`audit-logger`)
- **File:** `services/audit-logger/src/main/java/com/bofa/audit/AuditLogger.java`
- **Defect:** `classify` no longer special-cases `AUTH_FAILURE`, so a failed login is
  classified `WARN` instead of `CRITICAL`. Brute-force auth failures would not trip
  the SOC's critical-alert threshold.
- **Observed:** `classify(AUTH_FAILURE / FAILURE)` returns `WARN` (should be `CRITICAL`).
- **Why CI is green:** `InMemoryAuditLogStoreTest` covers store happy-path only;
  `classify()` is untested.
- **Edge test to write live:**
  ```java
  AuditEvent e = AuditEvent.builder().type(AuditEventType.AUTH_FAILURE)
          .outcome(AuditOutcome.FAILURE).actor("customer-demo")
          .message("bad creds").timestamp(Instant.EPOCH).build();
  assertEquals(Severity.CRITICAL, logger.classify(e));
  ```
- **The fix:** restore the `AUTH_FAILURE → CRITICAL` branch inside the
  failure/denied block.

---

## Running the demo

1. Check out this branch and confirm CI / `mvn -B verify` is **green** (bugs hidden).
2. Pick a scenario and follow the playbook: write the edge-case test → watch it fail.
3. Have Devin fix the **production code** (never the test), re-run, confirm green.
4. Show the before/after coverage delta and the resulting PR.

All identifiers used here are synthetic (see `SYNTHETIC_DATA.md`).
