# ALPHA-001 — First Cloud Upload Correction Decisions

## 1. Status

**Decision status: APPROVED FOR NARROW IMPLEMENTATION**

This document defines the corrective scope for the v0.13.0 Internal Alpha blocker:

**ALPHA-001 — First Cloud Upload Missing Revision**

This is a narrowly scoped blocker correction required to resume the local synchronization and dependent offline/recovery alpha lanes.

It is not a new product version and does not replace:

- v0.13.0 — Internal Alpha
- v0.13.0.1 — Hosted Supabase Deployment
- v0.13.1 — Controlled Real-Tournament Beta

---

## 2. Governing Context

The correction is governed by:

- `docs/project-workflow/101_V0_13_0_INTERNAL_ALPHA_DECISIONS.md`
- the current merged synchronization architecture on `main`
- the existing revision-safe-write contract
- the existing local Room revision state
- the existing Supabase `write_tournament_snapshot` RPC
- existing conflict and finalized-data protection rules

The v0.13.0 decisions require blocker findings to be recorded and independently reviewed before production code is changed.

ALPHA-001 has been recorded outside Git in the controlled internal-alpha evidence area.

---

## 3. Observed Alpha Failure

The controlled alpha tournament had:

- `local_revision = 33`
- `base_cloud_revision = NULL`

The first tournament upload produced:

- `status = FAILED_CONFLICT`
- `failureCategory = MISSING_REVISION`
- `attemptCount = 0`

A separate newly created synchronization fixture had:

- `local_revision = 1`
- `base_cloud_revision = NULL`

Its immediate upload produced:

- `status = FAILED_VALIDATION`
- `failureCategory = FAILED_VALIDATION`
- `attemptCount = 0`

The primary blocker addressed by this decision is the first-upload revision behavior after legitimate local tournament preparation.

---

## 4. Root Cause

`LocalRevisionState.expectedRevisionForWrite()` currently derives first-create eligibility from the local edit counter.

Current behavior is effectively:

- known `baseCloudRevision` -> use that cloud revision;
- `localRevision == 1` with no cloud base -> use expected revision `0`;
- otherwise -> no write expectation.

This incorrectly treats legitimate local edits performed before the first cloud synchronization as evidence that a cloud revision should already exist.

`localRevision` is a local change counter.

`baseCloudRevision` is the record of the last known cloud synchronization baseline.

Those concepts must not be conflated.

A never-synchronized tournament may legitimately have:

- `localRevision > 1`
- `baseCloudRevision = null`

and must still retain an explicit first-cloud-create expectation.

---

## 5. Approved Behavioral Correction

For a valid local revision state:

1. If `baseCloudRevision` exists, use that revision as the expected cloud revision.
2. If `baseCloudRevision` does not exist and a valid local revision exists, use expected revision `0`.
3. If no valid local revision state exists, return no write expectation.

Expected revision `0` remains an explicit create expectation only.

It must never be persisted as a cloud revision.

---

## 6. Server-Side Safety

The existing Supabase `write_tournament_snapshot` RPC already defines revision `0` as first-create behavior.

For a missing cloud tournament:

- expected revision must be `0`;
- authenticated ownership must match;
- the tournament may then be created.

For an already existing cloud tournament:

- expected revision `0` does not match its positive cloud revision;
- the RPC returns a conflict rather than silently overwriting existing cloud state.

Therefore this correction does not require weakening server-side optimistic concurrency.

---

## 7. Approved Production File Boundary

The approved production-code boundary is exactly:

`app/src/main/java/com/hoggamers/rankforge/domain/sync/RevisionConflict.kt`

The intended production change is limited to the first-write expectation logic.

No unrelated revision, divergence, queue, repository, mapper, networking, authentication, or synchronization behavior may be changed.

---

## 8. Approved Test Boundary

The approved regression-test boundary is exactly:

`app/src/test/java/com/hoggamers/rankforge/domain/sync/RevisionConflictTest.kt`

Required regression coverage must prove at minimum:

1. missing revision state still has no write expectation;
2. a new state with local revision 1 and no cloud base returns expected revision 0;
3. a never-synchronized state with local revision greater than 1 and no cloud base also returns expected revision 0;
4. a synchronized state with a cloud base continues to use the known cloud revision;
5. existing divergence and conflict behavior remains unchanged.

Additional test-file changes require separate justification.

---

## 9. Explicitly Excluded Changes

This correction must not modify:

- Supabase migrations;
- database schema;
- uniqueness constraints;
- RLS;
- RPC implementations;
- Storage policies;
- Edge Functions;
- hosted Supabase;
- `TournamentCloudUploadMapper`;
- cloud payload formats;
- synchronization queue schema;
- Room schema;
- Room migrations;
- tournament creation behavior;
- team-slot rules;
- roster validation;
- scoring;
- standings;
- OCR;
- screenshot processing;
- export;
- navigation;
- Compose UI;
- authentication configuration.

No opportunistic refactoring is allowed.

---

## 10. Data Safety Requirements

The correction must preserve:

- no silent overwrite of an existing cloud tournament;
- positive persisted cloud revisions;
- explicit revision-0 create semantics only;
- stale-write conflict behavior;
- local/cloud divergence detection;
- persistent queue behavior;
- local tournament data;
- finalized match protection;
- ownership enforcement.

No Room or Supabase data may be manually edited to make alpha verification pass.

---

## 11. Verification Requirements

After implementation, run at minimum:

- `.\gradlew.bat test`
- `.\gradlew.bat lint`
- `.\gradlew.bat assembleDebug`
- `git diff --check`

The focused revision tests must pass.

The complete JVM unit-test suite must pass.

No new Supabase migration is expected.

No hosted Supabase command is authorized.

---

## 12. Alpha Reverification Requirement

After the correction is merged into `main`, v0.13.0 Internal Alpha must resume using a build containing the merged correction.

The existing controlled alpha tournament should be used where safe.

The local synchronization lane must then verify:

1. first tournament upload succeeds against isolated local Supabase;
2. a positive cloud revision is returned;
3. the Room revision state records the cloud baseline correctly;
4. subsequent synchronization uses the positive cloud revision rather than create expectation `0`;
5. stale/conflict protection remains intact;
6. no duplicate tournament is created.

Only after successful initial synchronization may the dependent match synchronization and offline/recovery lanes resume.

---

## 13. Hosted Supabase Prohibition

This correction does not authorize any hosted Supabase modification.

The hosted project remains prohibited during v0.13.0.

Hosted deployment remains reserved for:

**v0.13.0.1 — Hosted Supabase Deployment**

---

## 14. Implementation Method

This correction is small and deterministic.

It should be implemented manually rather than using Codex unless unexpected complexity appears.

Approved implementation sequence:

1. merge this decision gate;
2. synchronize local `main`;
3. create a dedicated correction branch;
4. modify only the approved production helper and regression test;
5. run focused and full verification;
6. inspect the diff;
7. create and merge the correction PR;
8. return to v0.13.0 alpha execution;
9. rebuild and reinstall the corrected debug application;
10. resume local synchronization verification.

---

## 15. Completion Gate

The ALPHA-001 corrective implementation is complete only when:

- the approved behavior is implemented;
- regression coverage proves pre-first-sync local revisions greater than 1 remain create-eligible;
- existing revision safety tests pass;
- full JVM tests pass;
- lint passes;
- debug assembly passes;
- `git diff --check` passes;
- the implementation diff remains within the approved boundary;
- the correction PR is merged into `main`;
- v0.13.0 alpha synchronization is successfully re-exercised against isolated local Supabase.

The defect remains OPEN until alpha reverification succeeds.