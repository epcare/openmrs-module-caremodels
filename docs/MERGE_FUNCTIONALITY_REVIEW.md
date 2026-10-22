# Care Models merge functionality review — 22 October 2026

## Compared implementations and reference order

- Local `HEAD`: `cc3716f`, widget capabilities and typed REST data support.
- Incoming merge: `b0639b4` (includes `9861333`, `729b9fd`, `911f02e`, `8a6fee5`, `9572f00`).
- Shared base: initial scaffold `b0211a1`.
- Specification: engineering pack v2 `01-REQUIREMENTS.md`, `02-ARCHITECTURE-AND-DESIGN.md`,
  `03-AGENT-INSTRUCTIONS.md`; then explicit implementation decisions, sequenced plan and the
  concrete widget contracts in `WIDGET_CAPABILITIES.md`.

The existing merge state and all three stages of conflicted files were saved under
`/tmp/caremodels-merge-20261022` before resolving. No branch-wide ours/theirs replacement was used.
The user's unrelated `.gitignore` edit is preserved. The completed merge is committed locally; no push is performed.

## Functional comparison and retained behavior

| Area / objectives | Widget branch | Incoming enrollment branch | Combined result / evidence |
| --- | --- | --- | --- |
| PAT-002, NFR-001: lightweight membership | ACTIVE-only authorized projection; no configuration or resolvers | Preserves projection and adds enrollment persistence | Both retained; access boundary and filtered-read tests assert authorization and zero clinical calls |
| PAT-004/009, NFR-002: workspace access | Active membership, published model, patient/master/model checks; one selected tab; recorded pathway | Same security fix, typed envelope loading, tab summaries | Typed reader plus object guard; widgets/layouts/tabs/descriptors retained; suspended access denied |
| MOD-004: revision safety | Revision CAS and stale-write tests | Pessimistic row locks before draft/publish CAS | Incoming locking retained; lifecycle tests pass with widget definitions |
| MOD-006/007: imported templates | Schema/local-reference validation | Canonical checksum, unique version, basedOnVersion/revision and historical-reference validation | All retained; old missing-reference fixture now recomputes checksum to exercise its intended validation layer |
| MOD-005: administrative registry | AlreadyPaged response and writable name/description/privilege only | Same writable-property boundary | One implementation retained; query-filtered search/paging now exercised through RESTWS search path |
| JSON-005/006: widgets/properties | Six legacy + static/summary/care-plan types; strict properties/versions | Duplicate IDs, layouts and result compatibility | Sixteen type IDs retained; all incoming structural/rule checks retained; collection/scalar compatibility reconciled |
| JSON-003/004: clinical sources | Eight registered implementations, typed aliases, versions, bounds/results | Enrollment evidence is separate from widget resolution | Eight resolver implementations and source schemas remain intact; no arbitrary resolver enabled |
| PAT-005: multi-series | Named independent source bindings on selected tab | No replacement | Retained; each /data request independently authorized; no client-authored source definitions |
| PAT-006: forms/actions | Selected-tab descriptors and explicit unavailability | Enrollment service operations do not add REST mutations | Descriptors retained; owning-service authorization and launcher integration still required |
| JSON-008: care plan | Recorded member stage only | New bounded enrollment provenance/history | Both coexist in membership_data; no inferred milestone completion or copied clinical records |
| ENR-003/011: rules | No event-based enrollment engine | Strong Kleene logic, deterministic rule/action selection | Engine retained; type/qualifier/non-finite regressions added |
| ENR-004: event integration | Widget reads do not evaluate enrollment | Program/Encounter/Obs advice, trigger matching, after-commit processor | Incoming beans/advice retained; membership/workspace remain outside enrollment path |
| ENR-005/006/007: enrollment flows | Existing member services | Auto/proposal/manual/confirmation/decline, locked creation and replay checks | Incoming service flows retained; lifecycle integration now manually enrolls idempotently, authorizes workspace and reads its published patient-summary source |
| Exactly two tables | care_model and care_model_member | Reuses those tables; adds DAO queries/locks | No migration changes or new tables; static XML check confirms two createTable declarations |

## Resolutions that required functional choices

1. **CareModelAccessServiceImpl:** incoming typed envelope parsing is retained; the widget branch's
   non-object guard, selected-tab behavior and care-plan projection survive. Clinical readers do not
   need Manage Care Models. No eligibility engine is invoked on this read path.
2. **SemanticValidator:** union of source/property/binding validation with incoming trigger/rule,
   action availability, duplicate-ID, pathway and responsive layout checks. Charts support the
   existing NUMBER and TIMESERIES observation collections; tables retain bounded scalar-observation
   collections. Scalar metrics reject TABLE/TIMESERIES. The incoming test that called NUMBER a
   chart mismatch now tests STRING, while a new test explicitly protects the NUMBER contract.
3. **ModelResource:** retain one metadata property whitelist and AlreadyPaged response. Do not
   expose code/status/revision/configuration for generic updates.
4. **Automatic merge overlap:** remove duplicate `tabs` representation registration without removing
   any workspace property. Restore IOException import needed by the retained pathway parser.

## Bugs found beyond conflict markers

- **Filtered REST reads silently empty:** RESTWS calls `Searchable.search()` when q/status/patient/
  model/tab parameters are present. The three resources only overrode doGetAll, so filters could
  bypass useful listing logic and return the base EmptySearchResult. Added doSearch delegating to
  the same authorized doGetAll and five regression tests for metadata, workspace, denial, paging
  and update boundaries.
- **Import regression test stopped at checksum:** the old missing-reference fixture changed a
  published definition without updating its checksum. Recompute its checksum to test missing local
  references; independent integrity tests continue rejecting actual tampering.
- **Qualified measurement lost in enrollment:** incoming observation evidence returned numeric 50
  even when its modifier was `<`. Preserve qualified values as text, matching widget semantics;
  comparison with a numeric threshold is UNKNOWN, never an inferred exact measurement.
- **Numeric inference / non-finite values:** reject numeric-looking strings and type-mismatched
  equality/inequality as UNKNOWN; NaN/infinity must not throw or become enrollment facts.
- **Nondeterministic/latest evidence:** bound latest-observation query to one ordered result using
  obsDatetime descending and UUID ascending, and reject retired/voided/foreign/future evidence.
- **Patient identifiers in event logs:** replace patient/source/model IDs and exception detail with
  non-identifying failure classes; failures still signal that reconciliation is required.
- **Program replay read today's state:** use recorded states active at the supplied evaluation time,
  not getCurrentStates() evaluated at wall-clock time. Completion after that time remains future.
  Deterministic ties are applied to encounter/program/state evidence.

## Follow-up issues resolved

- Corrections matching a declared trigger family append deduplicated `FLAGGED_FOR_REVIEW`
  history without silently changing membership status. Generic suspend/resume/complete/withdraw
  transitions are locked, audited, idempotent and cannot resurrect terminal episodes. History is
  bounded to 100 entries and preserves enrollment provenance and recorded pathway state.
- New episodes enforce explicit reenrollment enablement and the configured cooldown after the
  last episode closes. Existing open episodes and source replays remain idempotent.
- Missed-event recovery runs through the core OpenMRS scheduler every 60 seconds, processing
  one model and at most 100 patients per transaction (default 50). A locked core global property
  stores the model/checksum/patient cursor, frozen evaluation time and patient high-water mark.
  Publication changes restart that model's scan. Failed pages retain their checkpoint; deterministic
  source UUIDs and enrollment locking make retries safe. Unavailable evidence retries the page;
  known absence permits progress. A broken dependency can therefore stall a page until repaired.
  This is recovery, not durable or exactly-once event delivery. No third module table is introduced.
- Conditional visibility evaluates only the requested tab after patient/master/model authorization;
  only TRUE grants access. Membership lookup and workspace discovery do not run eligibility rules.
- Validation and REST execution share the Spring-managed resolver registry. Trusted code extensions
  declare exact versions and source/result schemas; unknown IDs and malformed results fail closed.
  Built-in IDs cannot be replaced through extension registration. Execution records aggregate call,
  failure and duration counters without patient caches or identifiers. Read-only transactions have a
  10-second cooperative JDBC deadline; this is not hard wall-clock cancellation of arbitrary code.

## Remaining external acceptance work

- Forms/action/frontend adapters require installed launchers, approved destinations and underlying
  service authorization. Descriptors remain explicitly unavailable. No new clinical mutation endpoint
  or unapproved reminder/risk/adherence formula is fabricated. Autonomous pathways remain pending.
- MySQL 8 generated-column uniqueness and multi-session locking, installed event advice, authenticated
  HTTP/frontend behavior and representative p95 measurements need target-environment verification.
  The local Docker socket was inaccessible; H2 and mocks do not establish those production claims.
- Aggregate resolver counters do not establish full operational denial/conflict metrics or NFR-004.

## Verification

Every retained incoming rule/event/enrollment/import test and widget resolver/REST fixture test
runs with the new semantic, evidence, recovery, history, shared-registry and filtered REST regressions.
H2 coverage exercises draft → publish → idempotent manual enrollment → authorized selected workspace
and recorded transitions, plus bounded patient queries and the existing core checkpoint entity.
Resource tests use mocks; no authenticated HTTP or frontend browser verification is claimed.

### Final clean package

Command (module root; test-only logging and explicit agent configuration avoid local test harness
initialization/self-attachment restrictions):

```sh
mvn -o clean package \
  -Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory \
  -DargLine=-javaagent:/Users/lubwamasamuel/.m2/repository/net/bytebuddy/byte-buddy-agent/1.11.13/byte-buddy-agent-1.11.13.jar
```

**BUILD SUCCESS**: **182 tests** (167 API, 15 OMOD), zero failures/errors/skips.
The additional test verifies event failure logging contains no patient/source identifiers or exception
message. Namespace controller presence in the packaged OMOD and the unchanged two-table Liquibase
contract were checked. Artifact: `omod/target/caremodels-1.0.0-SNAPSHOT.omod`.

Relevant combined regression classes: `MergedSemanticContractTest`, `FilteredReadResourceTest`,
`EnrollmentEvidenceCompatibilityTest`, `ClinicalEventLoggingTest`, `CareModelLifecycleTest`,
`AccessBoundaryTest`, and both branches' original schema/rule/event/enrollment/import/resolver tests.
No existing subsystem tests were deleted. Two fixtures were adjusted to respect the combined
checksum and numeric collection contracts, as explained above.

Conflict resolutions and follow-up fixes are included in the local merge commit; no push is made. The `.gitignore` edit and untracked engineering-pack inputs remain untouched.
