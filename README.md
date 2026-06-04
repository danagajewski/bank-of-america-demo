# Consumer Digital Banking Platform

A polyglot microservice monorepo for a retail digital-banking platform. It mirrors
the kind of compliance-critical system a large US bank runs and that an **OCC
examination** scrutinizes: real-time money movement, customer authentication,
PII handling, and audit logging.

> **Demo context.** This repository is intentionally seeded at **low test
> coverage (~30% overall)**, with one service — `transaction-service` — shipped
> with **zero test infrastructure** (no test dependencies, no `src/test`, no
> coverage tooling). It exists so an autonomous engineer can scaffold testing from
> scratch and raise coverage on the exact code paths examiners care about. All
> data in code and tests is **synthetic** — see [`docs/SYNTHETIC_DATA.md`](docs/SYNTHETIC_DATA.md).

## Why these services
The four compliance-critical paths an OCC examiner targets map directly onto the
services here:

| Compliance path | Service | Language / Stack |
|---|---|---|
| Transaction processing | `transaction-service` | Java 17 · Spring Boot · Maven |
| Authentication (SSO/MFA, JWT sessions) | `auth-service` | Java 17 · Spring Boot · Maven |
| PII handling / masking | `pii-handler` | Java 17 · Maven (shared library) |
| Audit logging | `audit-logger` | Java 17 · Maven (shared library) |
| Real-time customer alerts | `notification-service` | TypeScript · Node · Jest |
| Fraud risk scoring | `fraud-detection` | Python 3 · pytest |

`transaction-service` depends on `pii-handler` and `audit-logger`; `auth-service`
depends on `audit-logger` — so tests must reason about cross-service behavior, not
just isolated functions.

### Supporting services (additional coverage surface)
Non-compliance-critical services that round out the platform and give more room to
grow coverage:

| Capability | Service | Language / Stack |
|---|---|---|
| Account balances (ledger vs available, holds, overdraft) | `balance-service` | Java 17 · Spring Boot · Maven |
| Account/transaction fee assessment | `fee-engine` | Python 3 · pytest |
| Customer card controls (lock/unlock, limits, authz) | `card-management-service` | TypeScript · Node · Jest |

## Coverage baseline (the gap to close)

| Service | Tooling | Baseline coverage |
|---|---|---|
| `transaction-service` | **none configured** | **0% — no test infrastructure** |
| `auth-service` | JUnit 5 + Mockito + JaCoCo | ~45% (happy-path login only) |
| `audit-logger` | JUnit 5 + JaCoCo | ~34% |
| `pii-handler` | JUnit 5 + JaCoCo | ~33% |
| `notification-service` | Jest | ~25–30% |
| `fraud-detection` | pytest + pytest-cov | ~31% (rules engine) |
| **Java monorepo aggregate** | JaCoCo `report-aggregate` | **~34%** |

The existing tests deliberately cover only easy, happy-path branches. The
**error-handling and validation branches** — the ones examiners review — are
uncovered. Concretely, the following are *not yet tested*:

- `TransactionService` — null/negative amount rejection, "no partial transaction
  persisted on failure", idempotency replay, per-transaction limit rejection.
- `AuthService` — expired-token rejection, "no session created on expiry",
  audit-event emission on failed auth.
- `PIIHandler` — SSN masking, "raw value never appears in a log line".
- `AuditLogger` — failed-auth event classification, timestamp stamping.

## Repository layout
```
.
├── pom.xml                         # Maven reactor (parent) for the Java services
├── services/
│   ├── transaction-service/        # Java — NO test infra (scaffold target)
│   ├── auth-service/               # Java — JWT sessions, 401 handling, audit
│   ├── pii-handler/                # Java — SSN/PAN/account/email masking
│   ├── audit-logger/               # Java — classified, timestamped audit trail
│   ├── balance-service/            # Java — ledger vs available balance, holds
│   ├── coverage-report/            # Java — JaCoCo monorepo-wide aggregate report
│   ├── notification-service/       # TypeScript — fraud/txn/balance/regulatory alerts
│   ├── card-management-service/    # TypeScript — card lock/unlock, limits, authz
│   ├── fraud-detection/            # Python — rules-based risk scoring
│   └── fee-engine/                 # Python — overdraft/maintenance/ATM/wire fees
├── docs/SYNTHETIC_DATA.md          # Synthetic-data policy (no real PII, ever)
└── .github/workflows/ci.yml        # CI: Java + TypeScript + Python
```

## Build & test

### Java (all services + aggregate coverage)
```bash
mvn -B verify
# Per-service HTML report:   services/<svc>/target/site/jacoco/index.html
# Monorepo aggregate report: services/coverage-report/target/site/jacoco-aggregate/index.html
```

### TypeScript — `notification-service`, `card-management-service`
```bash
cd services/notification-service   # or services/card-management-service
npm ci
npm run build          # tsc type-check
npm run test:coverage  # jest with coverage
```

### Python — `fraud-detection`, `fee-engine`
```bash
cd services/fraud-detection        # or services/fee-engine
pip install -e ".[dev]"
pytest                 # runs with coverage (pytest-cov)
```

## Toolchain
- Java 17 (Temurin), Maven 3.6+
- Node 20+, npm
- Python 3.10+ (CI uses 3.12)

## CI
`.github/workflows/ci.yml` runs three independent jobs — Java (`mvn -B verify`),
TypeScript (`npm ci` + Jest), and Python (`pytest`) — on every push and PR.
**No coverage thresholds are enforced**: the low baseline is intentional, and the
build is green so new test PRs can be merged through the normal review flow.
