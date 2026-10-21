# Care Models Engineering Decisions

## 2026-10-21 — Runtime definition loading

The patient workspace reads the published snapshot from the already-authorized `CareModel` entity inside `CareModelAccessService`. It does not call the administrative `getPublishedDefinition` service method, because that method correctly requires model-management privileges and ordinary clinicians must not receive that privilege.

The published definition is exposed only after master privilege, patient access, model privilege, published model status and ACTIVE membership checks pass.

## 2026-10-21 — Draft/publish compare-and-swap

Draft and publish operations acquire a pessimistic database write lock before reading the storage envelope revision. This makes revision validation and the subsequent row update one serialized transaction without adding a column or table.

The existing envelope and column revision remain the externally visible revision. A separate Hibernate-generated revision was not introduced because it would create two independently advancing revision values.

## 2026-10-21 — Pull request scope

The first implementation pull request is limited to access-control correctness, revision-write safety, regression tests, and the sequenced implementation plan. Enrollment evaluation and additional resolvers follow in separate reviewable changes.

## 2026-10-21 — Imported envelope trust boundary

An imported envelope is treated as untrusted input even after JSON Schema validation. Import now recomputes every published checksum, rejects duplicate snapshot versions or a draft based on a non-current version, validates every history definition against local references, and requires the selected privilege to be registered when the import contains a published snapshot.

## 2026-10-21 — Registry REST update boundary

The generic model REST update accepts only `name`, `description`, and `privilege`. Code, status, revision, and configuration are read-only on that route; lifecycle and envelope mutations remain confined to the dedicated operation endpoints.

Resolver-ID registration is intentionally deferred until the static resolver holder is replaced by the Spring-managed extension registry in Phase 4. Enforcing it against today’s private static registry would reject legitimate future extensions while providing no shared runtime registration point.

## 2026-10-21 — Enrollment rule semantics

Rule expressions use strong Kleene three-valued logic. A resolver distinguishes known absence from unavailable evidence; unavailable data and lookup failures evaluate to `UNKNOWN`, and `UNKNOWN` never selects an enrollment action by itself. Window predicates require an explicit evaluation time so replay and reconciliation are deterministic.

When multiple rules are true, the engine uses the conservative action order `EXCLUDE`, `PROPOSE_CONFIRMATION`, then `AUTO_ENROLL`. Rules with the same action are selected by lexical rule ID, so reordering the JSON array does not change the outcome. All individual evaluations remain available for provenance and diagnostics.
