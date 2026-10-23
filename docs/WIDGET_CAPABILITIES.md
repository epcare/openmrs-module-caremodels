# Widget backend capabilities — release 1.0.0-SNAPSHOT

## Initial inspection (2026-10-21)

No applicable AGENTS.md was found. Frontend status, registry, properties, REST parsers and
all five designer schema copies were inspected before editing. Those copies match the baseline
backend schemas. The six widget types and only latest-observation resolver are the initial state.
Clinical Status is metric/presentation=status. Workspace returns one authorized tab and safe tab
summaries; membership never calls a resolver. No new tables or endpoints are authorized.

| Area | Initial executable capability | Initial gap |
| --- | --- | --- |
| Metric/status/chart/table | latest-observation only | dates, bounds, deterministic ordering, typed collections |
| Timeline | observation compatibility | actual encounters |
| Static text/heading/divider/spacer/callout | none | types and validated properties |
| Conditions/medications/program/patient | none | registered typed implementations |
| Forms/actions | references only | selected-tab descriptors, runtime availability |
| Summary chart | one source | multi-bindings and independent source authorization |
| Reminders/risk/adherence/derived/pathway state | none | approved methods, clinical semantics, current state contracts |

Implementation evidence and final contract follow below; frontend enablement remains separate.

## Delivered backend contract

No endpoints or database tables are added. The existing `/membership`, `/workspace`, `/data`,
`/model` and `/modeloperation` resources and REST namespace remain. `resultJson` remains a
JSON-encoded string, not a nested REST object. Definition/envelope and revision/conflict contracts
remain unchanged. Existing six widget types and the four original definition fixtures are tested.

### Widget IDs, versions and properties

All supported component versions are `1.0.0`; omitted version means `1.0.0`. The authoritative
per-type property schemas are in `api/src/main/resources/schemas/model-definition.schema.json`.
Unknown properties are rejected; properties are never executed. All grid dimensions retain their
bounds (12 columns, height 1–20, rows 0–100). `col + w` must fit the grid.

| Catalog | Persisted type / mapping | Property defaults and restrictions | Source |
| --- | --- | --- | --- |
| Clinical Metric | `metric` | title/subtitle/description/emptyText/emptyMessage; unit ≤20 chars; decimals/precision 0–4; optional exact status mappings, dates, neutral reference limits | observation |
| Clinical Status | `metric`, `properties.presentation=status` | exact-value mappings with approved tag tones; no inferred clinical state | coded/text observation |
| Trend / Summary Chart | `trend-chart` | chartType line/bar (line default); scaleType linear/log (linear default); showDataTable false default | numeric observation source(s) |
| Clinical Table | `clinical-table` | pageSize 5–50 (frontend default 5), observation columns/labels, sortBy and sortOrder | observation or typed records; typed record renderer still needed |
| Encounter Timeline | `timeline` | common display text | `encounter-query`; legacy observation binding remains compatible |
| Form Launcher | `form-launcher` | buttonLabel/buttonStyle/orientation plus common display text | formAlias or additive formAliases[] |
| Quick Actions | `action-panel` | actionLabels/buttonStyle/orientation plus common display text | declared actionIds |
| Text / Note | `text-note` | required text, max 4000 chars; plain text only, no HTML/Markdown execution | none |
| Section Heading | `section-heading` | required text; level 2–6, default 2 | none |
| Divider | `divider` | orientation horizontal/vertical, default horizontal | none |
| Spacer | `spacer` | size small/medium/large, default medium | none |
| Static Alert / Callout | `static-callout` | required text; optional title; tone info/success/warning/error, default info. This is authored guidance, never a clinical alert | none |
| Diagnosis / Conditions | `condition-list` | common display text | `conditions` or `encounter-diagnoses` |
| Medication Summary | `medication-summary` | common display text | `order-query`; explicitly concept-scoped order history |
| Program Summary | `program-summary` | common display text | `program-state` |
| Patient Summary | `patient-summary` | common display text | registered `patient-summary` |
| Care Plan / Milestones | `care-plan` | title/emptyText | only recorded membership pathway stage; no source or completion inference |

Common display text means title/subtitle/description/emptyText/emptyMessage, where applicable
in the schema. Defaults describe consumer behavior; validation never rewrites a saved definition.
Static widgets and care-plan cannot bind clinical sources or actions. New clinical summary types
require the matching source. Layout types are not aliases for metrics. Text must be rendered as
text by the frontend, never inserted into HTML.

### Source declarations and result schemas

`data-source.schema.json`, Java `SourceContract`, and resolver registration jointly validate the
executable contract. A schema type alone is insufficient. Unknown resolver IDs, versions, aliases,
fields and unsupported source options fail validation. Generic built-ins use their `type` directly;
the three named `registered` sources require resolverVersion `1.0.0` and resultType `TABLE`.

| ID / version | Declaration | Defaults, window and ordering | Result |
| --- | --- | --- | --- |
| latest-observation / 1.0.0 | type, conceptAlias, resultType; optional units, effectiveTime=OBS_DATETIME, maxResults, windowDays, includeVoided=false | 1 result; no lower date bound when window omitted; effective time descending, UUID ascending ties | existing type/concept/values[] contract |
| historical-observations / 1.0.0 | same observation declaration | 100 results, 365 days; same deterministic ordering | same values[], with collection metadata |
| encounter-query / 1.0.0 | encounterTypeAlias, resultType=TABLE | 100 results, 365 days; encounterDatetime descending, UUID ascending | real encounter records; status `recorded` means a non-voided record, not workflow completion |
| program-state / 1.0.0 | programAlias, resultType=TABLE | 100 episodes enrolled in past 3650 days; dateEnrolled descending, UUID ascending | separate enrollment episodes, completion dates, up to 50 current states each; statesTruncated explicit |
| order-query / 1.0.0 | conceptAlias, resultType=TABLE | 100 orders activated in past 365 days; dateActivated descending, UUID ascending | recorded order actions, start/stop/expiration, previous-order link, core isActive(assessmentTime), drug/dose metadata |
| conditions / 1.0.0 | type=registered, resolverId, resolverVersion, resultType=TABLE | 100 conditions, 3650 days by onset or recorded date if onset missing; most recent first then UUID | longitudinal conditions; coded or non-coded name, authoritative clinical/verification status, onset/end date; superseded versions excluded |
| encounter-diagnoses / 1.0.0 | same registered declaration | 100 diagnoses, 365 days by encounter date; descending encounter date then UUID | encounter-linked diagnoses and recorded certainty; distinct from longitudinal conditions |
| patient-summary / 1.0.0 | same registered declaration, fields[] | explicit unique subset of name/gender/birthdate/birthdateEstimated/dead/deathDate; no window or maxResults | fields object containing only selected fields |

All collection limits are 1–1000 and windows 1–3650 days when supplied. No voided patient,
observation, encounter, enrollment, state, order, condition or diagnosis is returned. Resource
aliases resolve through the appropriate terminology map; retired concept/program/encounter-type
references fail closed at runtime. Drugs and order types are returned as recorded provenance;
there is no guessed alias, drug UUID substitution, order-type override or configurable order payload.

**Query limits:** Observation queries send date and row bounds to the OpenMRS API (limit+1,
for truncation detection). Encounter/program/order APIs in 2.7.4 accept patient/resource/date
filters but no row limit. Their matching records are sorted and capped in memory. ConditionService
has only patient-scoped collection methods; conditions are date-filtered and capped after that call.
These limits bound responses, not database scans on those APIs. Very large histories need
performance verification before enabling those capabilities broadly. No raw SQL or replacement
DAO was added to manufacture paging. Every response has `total:null`; it never presents a
bounded subset as complete patient history.

`clinical-result.schema.json` specifies exact versioned result shapes. Collection envelopes contain
`resolverVersion`, `assessedAt` (UTC ISO-8601), `provenance`, `status`, `returnedCount`, `truncated`
and `total:null`. Typed record sources use `records[]` and declared `availableFields`; the patient
source uses `fields`. `ok` means recorded results; `no-records` means no eligible records in that
source's window. Unavailable capability, invalid configuration and permission failures are REST
errors, not an empty clinical history. Missing pathway evidence is `insufficient-data`.

Observation compatibility is deliberate: `values[].obsUuid`, effectiveTime, valueType, value and
optional valueName remain. Numeric comparators are preserved as STRING values such as `<50.0`,
never NUMBER 50, and no new comparator/qualifier keys are silently injected into old consumers.
Text values such as `<50` remain verbatim text. Units come from the numeric concept; no conversion,
interpolation or medical threshold computation occurs. Dates now use unambiguous UTC ISO strings.
Clinical Status remains an explicit value/label mapping supplied by configuration.

Medication results are **not a complete current regimen**: the selected concept and activation
window limit scope. `activeAtAssessment` is the core order API's answer, not the latest order
being labelled current. No dose normalization, treatment recommendation or substitution is done.
Program windows are enrollment-date windows, so older ongoing enrollments can be outside scope.
Condition windows are onset/recorded-date windows, so this is not an unbounded current-problem list.

### Multi-source charts

Existing `dataSource:"weight"` stays supported. The additive alternative is:

```json
{"id":"chart","type":"trend-chart","dataSources":["weight","height"],"properties":{"title":"Recorded measurements"}}
```

Each key names a separately declared observation source with its own conceptAlias, units, limits
and window. Up to eight distinct bindings; `dataSource` and `dataSources` are mutually exclusive.
Only trend-chart accepts multiple sources. The frontend must issue one existing `/data` call per
key. Each call independently reauthorizes patient/model/active membership/tab and proves the key
is bound to that selected published tab. There is no arbitrary runtime list of concept UUIDs.
Different units/scales must be displayed explicitly; axis handling and multi-series parsing/rendering
remain frontend work. Summary Chart is therefore partial, not enabled by this backend change.

### Selected-tab form/action descriptors

`GET /workspace?...&v=default` retains widgets/layouts/tabs and adds:

```json
{"descriptors":{"version":"1.0.0","forms":[{"alias":"followup","formUuid":"example-form","name":"Follow-up","version":"1","available":false,"reason":"frontend-form-engine-not-verified","encounterTypeUuid":"example-type","encounterTypeName":"Follow-up","resourceNames":["JSON schema"]}],"actions":[{"id":"list","type":"navigate-internal","label":"Patient list","handlerVersion":"1.0.0","available":false,"reason":"registered-authorized-destination-required"}]}}
```

Descriptors resolve **only** references used by selected-tab widgets or their declared actions.
`formAliases[]` adds up to ten forms while preserving existing single formAlias. Alias UUID and
forms[].formUuid must agree. Missing/retired/unpublished forms, missing/retired/mismatched encounter
types, missing read/create privileges and missing engine resources have distinct denial reasons.
No form definition, raw resource content/reference, arbitrary action target or administrative
model definition is exposed. Resource names are metadata only and do not prove engine support.

No launch adapter has been verified in this frontend/backend deployment. Accordingly, even
published forms with resources remain `available:false`, reason `frontend-form-engine-not-verified`.
Empty resource metadata yields `form-engine-resource-unavailable`. This is an intentional partial
contract, not a claim that the existing catalog makes a form launchable. The frontend must resolve
its installed registered launcher and preserve normal patient/visit/encounter context. No visit is
created by this module. The owning form/encounter service must authorize the eventual mutation.

Action types have fixed descriptor handling; **none is advertised as executable** by this release:

| Type | Unavailable reason / required integration |
| --- | --- |
| launch-form | selected form's availability reason; verified frontend engine adapter required |
| navigate-internal | registered-authorized-destination-required; an approved destination/list registry and destination authorization are absent |
| open-order-entry | registered-order-workspace-required; no arbitrary order POST payload is accepted |
| open-appointments | appointments-module-unavailable if module not started, otherwise registered-appointment-workspace-required |
| enrollment-transition | no-authorized-transition-rest-contract; service transitionMember exists and checks transition privilege, but no approved Care Models mutation endpoint is provided |
| action with availabilityRule | registered-availability-rule-required; rule declarations do not grant availability |

The form catalog remains the core `/ws/rest/v1/form` endpoint, including publication/retirement
and resource metadata. No discovery endpoint, mutation endpoint or new table is introduced.

### Current pathway and advanced capabilities

A selected `care-plan` widget requests `descriptors.pathway`. Only the already-authorized active
member's validated membership_data.pathway is read, after tab selection. If its recorded stage
matches a stage in the published enabled pathway, the descriptor is:

```json
{"status":"recorded","stage":"review","label":"Review","enteredAt":"2026-01-01T00:00:00Z","provenance":"care_model_member.membership_data.pathway"}
```

Otherwise status is `insufficient-data` and reason identifies disabled pathway, missing stage,
invalid metadata or a future stage date. No stage list, inferred completion, clinical history or
state-change operation is invented. No metadata read occurs without a selected care-plan widget.
Care reminders, dynamic clinical alerts, adherence methods, risk scores and derived calculations
remain unavailable: there are no approved registered rules, methods or validation evidence.
The registry rejects unregistered versions rather than fabricating results.

## Compatibility and frontend migration

Schema/storage version remains 1.0.0; the capability manifest is separately versioned. No saved
model is rewritten. The original six types and original fixtures still validate. Existing observation
consumers keep values[]. Existing scalar/form/action bindings continue to exist. Newly strict
property/version/source-option validation may reject previously accepted arbitrary properties,
unsupported source IDs or future versions **on edit/publication**. Run validate on exported drafts,
remove or map unsupported display options explicitly, and retain the original envelope for review.
Failed validation or revision conflict never overwrites the saved draft or increments its revision.
Already-published supported definitions continue to be read; unsupported execution fails closed.

Frontend handoff (not performed in this backend-only workspace):

1. Copy the authoritative model-definition and data-source schemas into designer/schemas and
   update TypeScript widget/source/workspace/result types. Preserve existing JSON-string fields.
2. Add the static and typed summary renderers and defaults above; render text as text. Keep
   Clinical Status as metric/presentation=status. Add separate encounter/condition/order/program
   record parsers instead of feeding records into observation renderers.
3. Test old observation fixtures, numeric qualifiers kept as strings, UTC dates, typed records,
   unavailable/missing states and unknown result versions before enabling any palette entry.
4. Add parser migration tests for dataSources[], formAliases[], descriptors and pathway; verify
   independent selected-tab source requests and zero patient queries in designer preview.
5. Integrate registered launch/navigation/workspaces and independently authorized owning services.
   Backend descriptors restrict availability; they never grant service permissions.
6. Run frontend verify/build, authenticated save/reopen/publish, and desktop/mobile shell checks.
   Backend tests do not certify those results.

## Final 25-widget capability matrix

All rows refer to **backend** support. New renderers and live HTTP verification are not claimed.
`api/src/main/resources/widget-capabilities.json` is the machine-readable release manifest.

| # | Widget | Backend state | Evidence / remaining dependency |
| --- | --- | --- | --- |
| 1 | Clinical Metric | Implemented/tested observations | numeric/coded/text/date, provenance, bounds; no medical inference |
| 2 | Clinical Status | Implemented/tested observations | metric/status retained; coded values and explicit mappings |
| 3 | Trend Chart | Implemented/tested source | bounded history, deterministic ties, qualified strings; frontend multi-series work separate |
| 4 | Clinical Table | Implemented/tested sources | bounded observation/typed records, fields/truncation; typed frontend tables pending |
| 5 | Encounter Timeline | Implemented/tested source | actual encounter records; frontend must stop using observation timeline for this source |
| 6 | Diagnosis / Conditions | Implemented/tested sources | separate longitudinal and encounter diagnosis types; typed renderer pending |
| 7 | Medication Summary | Partial | exact concept-scoped history; complete current-regimen semantics not supplied |
| 8 | Program Summary | Implemented/tested source | exact program and multiple enrollment episodes; enrollment-date window explicit |
| 9 | Patient Summary | Implemented/tested source | minimal explicitly selected fields; no patient data in definitions |
| 10 | Care Reminders | BLOCKED | approved reminder rules, actual due-date semantics and registered actions absent |
| 11 | Form Launcher | Partial | multiple safe descriptors; deployment engine/launcher verification absent |
| 12 | Quick Actions | Partial | selected registered descriptor types; executable frontend handlers absent |
| 13 | Order / Request Actions | BLOCKED | installed registered order-entry workspace and permission integration absent |
| 14 | Appointment Actions | BLOCKED | runtime module detection only; registered frontend appointment workspace absent |
| 15 | Patient List Link | BLOCKED | approved list/destination registry and destination authorization absent |
| 16 | Text / Note | Implemented/tested static contract | text-note; frontend renderer pending |
| 17 | Section Heading | Implemented/tested static contract | section-heading, bounded heading level |
| 18 | Divider | Implemented/tested static contract | divider, finite orientation |
| 19 | Spacer | Implemented/tested static contract | spacer, finite size set |
| 20 | Alert / Callout | Partial | static-callout tested; dynamic clinical alert rules absent |
| 21 | Care Plan / Milestones | Partial | authorized recorded stage only; no inferred milestone completion/mutation |
| 22 | Summary Chart | Partial | multi-bindings authorized independently; multi-series frontend scales/rendering pending |
| 23 | Adherence Indicator | BLOCKED | approved observation/method, period and missing-data specification absent |
| 24 | Risk Indicator | BLOCKED | clinically validated versioned risk method and inputs absent |
| 25 | Custom Metric (Derived) | BLOCKED | registered versioned calculation and input/unit/effective-date specification absent |

## Milestone evidence and file groups

1. Static: Registry, model-definition schema, SemanticValidator, StaticWidgetTest and registry/schema
   synchronization. Initial focused run: 29 tests passed, including original fixtures.
2. Clinical: resolver package, data-source and clinical-result schemas, DataRequestResource,
   SourceContract and ClinicalResolversTest. Typed results use existing /data, with no new request
   source definitions accepted. Bounds and ordering verified with mocked OpenMRS service APIs.
3. Forms/actions: WorkspaceDescriptors, WorkspaceResource/WorkspaceTab and PatientResourceContractTest.
   Existing v=default widgets/layouts/tabs retained; descriptors additive; unavailable engines/actions
   remain disabled. No clinical mutation implemented.
4. Advanced: selected member-pathway projection in access service, care-plan schema and access tests;
   unregistered clinical calculations rejected. Capability manifest and this handoff record blockers.

Access changes reject suspended memberships and read only the authorized published snapshot without
calling the admin-only envelope/definition API. Membership lookup and workspace discovery tests
assert zero resolver/clinical calls. REST tests are resource-level mocks, not authenticated HTTP.
The existing lifecycle/access suite uses H2 and does not establish MySQL generated-column behavior.

### Result fixtures and final verification

`api/src/test/resources/results/` contains ten synthetic, de-identified contract fixtures:
scalar, coded status, time series, observation table (qualified text), encounters, conditions,
diagnoses, medications, program episodes and selected patient fields. They are test/handoff data
only and are never loaded by runtime resolvers. `ResultFixtureTest` validates them against the
result schema and proves that a qualified string cannot masquerade as an exact NUMBER.
The real frontend TypeScript parser migration tests remain pending in the frontend repository.

Final command, run from `/Users/lubwamasamuel/Projects/mets/openmrs/modules/openmrs-module-caremodels`:

```sh
mvn -o clean package \
  -Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory \
  -DargLine=-javaagent:/Users/lubwamasamuel/.m2/repository/net/bytebuddy/byte-buddy-agent/1.11.13/byte-buddy-agent-1.11.13.jar
```

Result: **BUILD SUCCESS**, 86 API tests + 10 OMOD tests = **96 tests**, zero failures/errors/skips,
23.155 seconds. The explicit test agent avoids this sandbox's blocked dynamic JVM self-attachment;
the logging override avoids OpenMRS data-directory initialization in plain unit tests. Neither
changes runtime module security or JVM configuration. Mockito/JUnit changes are test-scope only.
The standard inherited formatting and licensing checks ran in this command. Compiler messages
about Java 8 target on JDK 11 and legacy Groovy reflective access remain existing build warnings.

Artifact: `omod/target/caremodels-1.0.0-SNAPSHOT.omod`. No deployment, authenticated HTTP,
frontend build, browser screenshot or MySQL-backed concurrency verification was performed.
No production clinical records were touched. The next milestone belongs to frontend integration
and deployment verification; capabilities with missing clinical rules or workflow adapters remain
blocked as specified in the matrix.

## Merge follow-up runtime contract (22 October 2026)

The eight built-ins remain the default capability manifest. Validation and `/data` execution share
`caremodels.DataResolverRegistry`. Trusted Spring `VersionedDataResolver` beans may add IDs with
exact semantic versions and explicit source/result JSON schemas; definitions cannot install code,
replace built-in IDs or enable unknown resolvers. Returned payloads are validated before delivery.
Execution uses a read-only 10-second cooperative transaction deadline and aggregate per-resolver
call/failure/duration counters. There is no patient-data cache or hard wall-clock worker cancellation.

Selected conditional tabs now evaluate visibility after all access checks; FALSE/UNKNOWN deny.
Unselected tabs, membership discovery and workspace discovery do not evaluate eligibility rules.
Forms/actions and unapproved clinical algorithms retain the availability restrictions above.
