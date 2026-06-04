# Synthetic Data Policy

This repository is a demonstration environment. **No real customer data may ever
appear in source code, tests, fixtures, logs, or commit history.**

Any time a test or example needs a sensitive identifier, use synthetic values
that are obviously fake and never collide with real records:

| Field | Use | Never |
|---|---|---|
| SSN | `123-45-6789`, `000-00-0000` | a real 9-digit SSN |
| Card / PAN | test BINs such as `4000 0000 0000 0002` | a real PAN |
| Account number | `1234-5678-9012`, `0000-0000-1111` | a real account number |
| Email | `customer-demo@example.com` | a real customer address |
| Username | `customer-demo`, `teller-demo` | a real login |

## Conventions enforced in code
- All PII must be routed through `pii-handler` (`com.bofa.pii.PIIHandler`) before
  it is logged, rendered, or transmitted. Raw values must never reach a log line.
- The `notification-service` redacts account numbers out of every message body
  before delivery (`redactAccountNumbers`).
- Audit events store **masked** account references only (see
  `TransactionService` → `AuditLogger`).

When generating new tests, carry this convention forward: assert on the *masked*
output and assert that the *raw* value is absent from any captured log output.
