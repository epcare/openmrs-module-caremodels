# Care Models Backend Implementation Plan

This plan follows the 2026-10-21 v2 engineering baseline. Work is split into focused pull requests so security and persistence invariants are reviewed before the enrollment engine and clinical resolver surface expand.

## Phase 1 — Runtime access and persistence safety

- Require an `ACTIVE`, non-voided membership on every workspace and data request.
- Allow an authorized clinician to load the selected published definition without granting model-management privileges.
- Serialize draft and publish writes with a database row lock so revision comparison and persistence are atomic.
- Add regression coverage for suspended membership denial and clinician runtime access.
- Keep the membership listing configuration-free and resolver-free.

## Phase 2 — Contract and lifecycle hardening

- Complete semantic validation for enrollment triggers/rules, visibility rules, pathway transitions, layout references, resolver IDs and widget/result compatibility.
- Verify imported history definitions, canonical checksums and envelope revision consistency.
- Prevent generic metadata updates from bypassing lifecycle and envelope invariants.
- Return inner-tab summaries in the workspace REST representation.
- Add REST resource tests and MySQL migration/concurrency tests.

## Phase 3 — Enrollment engine

- Implement three-valued rule evaluation and deterministic priority.
- Add filtered Program, Encounter and Observation event listeners after committed clinical changes.
- Implement idempotent automatic, confirmation and authorized manual enrollment flows.
- Persist bounded transition/provenance history in `membership_data`.
- Handle corrected/voided evidence through review flags.
- Provide resumable bounded reconciliation/backfill without adding a third module-owned table.

## Phase 4 — Clinical data and actions

- Replace the static resolver registry with a Spring-managed extension registry.
- Implement historical observation, program-state, encounter and order resolvers.
- Dispatch custom sources by `resolverId` and enforce declared versions/result schemas.
- Add limits, timeouts, provenance, units, ISO-8601 effective times and safe cache boundaries.
- Add guarded form/action execution using the underlying OpenMRS privileges.

## Phase 5 — Verification and operations

- Run API and OMOD tests on the supported Java/OpenMRS matrix and MySQL 8.
- Add concurrency, idempotency, authorization, correction and missed-event recovery tests.
- Add redacted metrics/logging for access denial, validation, resolver latency and publish conflict.
- Record performance measurements for membership, workspace and common resolver calls.

No phase may add a third Care Models table, facility-scoped runtime behavior, JSON-embedded privileges, chart-open rule evaluation, or resolver work on the membership-list path.
