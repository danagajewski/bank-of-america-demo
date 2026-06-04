# Playbook: Improve test coverage on a compliance-critical path

A reusable runbook for raising test coverage on a compliance-critical code path
(transaction processing, authentication, PII handling, audit logging) — and, when
a new test reveals that the code is actually wrong, fixing the production code.

This is the repo-readable copy of the Devin playbook of the same name. Point Devin
at it (or run the saved playbook) and supply a target service/path.

## When to use
- A service or code path is below the coverage bar an OCC examiner expects.
- You want edge-case / error-handling / data-validation coverage, not just happy paths.
- A service has **no test infrastructure** yet (no framework, no CI test job).

## Inputs
- `target`: service + path to improve (e.g. `pii-handler` / `PIIHandler.maskSSN`).
- Optional: a coverage goal or a specific branch/behavior to cover.

## Procedure
1. **Scope the gap.** Read the target code and its existing tests. Run the suite with
   coverage (`mvn -B verify` / `jest --coverage` / `pytest --cov`) and list the
   **uncovered branches** — focus on error handling and data validation.
2. **Scaffold if missing.** If the service has no test infra, add the minimal setup
   only (test dependency, config, one CI job). Match the conventions of a sibling
   service in the same language. Do not add coverage thresholds yet.
3. **Write edge-case tests first.** For each uncovered branch, write a focused test
   that asserts the *correct* behavior per the spec/Javadoc — boundary values,
   null/invalid input, "no side effect on failure", idempotency, masking exactness,
   severity/classification.
4. **Run.** Execute the new tests.
   - If they **pass**, coverage went up — continue to the next branch.
   - If a test **fails**, you have found a real defect. Go to step 5.
5. **Fix the production code, not the test.** Confirm the test encodes the intended
   behavior (re-read the spec). Then fix the source so the test passes. Never weaken a
   test to make it green. Re-run the full suite to check for regressions.
6. **Keep CI green.** Run lint/build/test for the affected stack. Ensure all jobs pass.
7. **Open a PR.** Summarize: branches now covered, coverage delta, and **any bug the
   tests caught and how it was fixed** (this is the headline for compliance reviewers).

## Guardrails
- Synthetic data only — never real customer PII in fixtures (`docs/SYNTHETIC_DATA.md`).
- Fix code to match the spec; if the spec itself is ambiguous, flag it rather than guess.
- Keep changes minimal and scoped to the target path.

## Definition of done
- New edge-case tests exist and pass; previously uncovered branches are covered.
- Any defect surfaced by a test is fixed in source (test unchanged) with no regressions.
- CI is green and a PR is open with a coverage + defects summary.
