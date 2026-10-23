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

## 2026-10-22 — Reconcile enrollment and widget implementations

The merge retains the incoming enrollment engine, after-commit event advice, locked enrollment
persistence and import-integrity validation alongside the widget branch's sixteen widget type IDs,
eight executable resolvers, multi-source authorization and selected-tab descriptors. No tables,
endpoint names or patient response fields are removed or added by the reconciliation.

Runtime access uses the incoming typed `StorageEnvelope` reader inside the already-authorized
access service and retains the widget branch's object-shape guard and recorded-pathway projection.
It continues to reject inactive memberships and never calls administrative envelope APIs for a
clinical viewer. Conditional visibility evaluates only the selected authorized tab and grants access only for TRUE.

The earlier resolver-registration deferral is superseded for the eight implemented built-ins: their
IDs, versions and options remain validated by `SourceContract`. Unregistered code must not become
executable merely to accommodate hypothetical future extensions. Validation and runtime now share a Spring-managed registry with trusted versioned code extensions.

Widget compatibility follows the working observation wire contract: numeric observation collections
may serve charts/tables, while scalar metrics reject TABLE/TIMESERIES. Incoming duplicate-ID,
layout, pathway and nested rule validation is retained. The existing decision to prefer confirmation
over automatic enrollment when both match is retained; the older architecture sketch lists a
different order and must not be used to silently reverse this reviewed behavior.

RESTWS sends query-filtered collection reads through `search()`. Membership, workspace and model
resources now delegate that path to the same authorized/filtering code as their list path. Existing
paging and the restricted metadata update property set are preserved.

Enrollment evidence now preserves observation qualifiers, rejects non-finite/mismatched numeric
comparisons, orders latest observations deterministically, and resolves program states at the supplied
evaluation time. These align clinical-event evaluation with the widget branch's typed-value contract;
no scoring formula or new clinical rule is introduced.

See `docs/MERGE_FUNCTIONALITY_REVIEW.md` for the comparison, test evidence and remaining project
acceptance gaps. A passing merge build is not evidence that every planned phase is implemented.

## 2026-10-22 — Complete merge follow-up safeguards

Correction events append bounded, deduplicated review history; they do not silently revoke or
transition membership. Generic transitions lock the model and append audited reasons. Terminal
episodes cannot reopen. Reenrollment is disabled by default and requires an explicit policy and
elapsed cooldown after closure.

Recovery reuses the core scheduler and one locked core global property, preserving exactly two
module-owned tables. One model and at most 100 patients run per batch (default 50, every 60 seconds).
The checkpoint freezes evaluation time and patient high-water mark; publication changes restart
that model. Failed pages retry with deterministic source identifiers. Unknown service evidence
stalls the page rather than silently skipping patients. This does not promise durable event delivery.
Operators may set `caremodels.reconciliation.enabled=false` or adjust
`caremodels.reconciliation.batchSize` (1–100). The internal cursor is
`caremodels.reconciliation.cursor`; reset only as an intentional replay operation.

Trusted Spring resolver extensions supply exact versions and source/result schemas. Validation and
execution share the registry. A read-only 10-second cooperative transaction deadline and aggregate
resolver calls/failures/duration counters apply; no patient cache or detached clinical worker is added.
This deadline does not forcibly cancel arbitrary extension code.
