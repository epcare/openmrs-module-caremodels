# Care Models Engineering Decisions

## 2026-10-21 — Runtime definition loading

The patient workspace reads the published snapshot from the already-authorized `CareModel` entity inside `CareModelAccessService`. It does not call the administrative `getPublishedDefinition` service method, because that method correctly requires model-management privileges and ordinary clinicians must not receive that privilege.

The published definition is exposed only after master privilege, patient access, model privilege, published model status and ACTIVE membership checks pass.

## 2026-10-21 — Draft/publish compare-and-swap

Draft and publish operations acquire a pessimistic database write lock before reading the storage envelope revision. This makes revision validation and the subsequent row update one serialized transaction without adding a column or table.

The existing envelope and column revision remain the externally visible revision. A separate Hibernate-generated revision was not introduced because it would create two independently advancing revision values.

## 2026-10-21 — Pull request scope

The first implementation pull request is limited to access-control correctness, revision-write safety, regression tests, and the sequenced implementation plan. Enrollment evaluation and additional resolvers follow in separate reviewable changes.
