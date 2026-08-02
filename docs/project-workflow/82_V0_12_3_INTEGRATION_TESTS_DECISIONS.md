# Phase 12 v0.12.3 — Integration Tests Decisions

## Status

**Approved for implementation after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.3 — Integration Tests**

Canonical scope:

> Test complete roster, match, OCR, scoring, persistence, and export workflows.

---

## 1. Purpose

Complete the remaining meaningful integration coverage across already-implemented Rank Forge components.

v0.12.3 verifies that production use cases, repositories, Room persistence, validation, finalization, OCR correction state, and downstream workflow behavior operate correctly together.

This version does not add new product functionality.

---

## 2. Core Decision

v0.12.3 is a **test-only integration version**.

The focused inventory found that most scoring, persistence, restart, and export integration behavior is already adequately covered.

Only one existing instrumentation test file requires additional integration coverage:

`app/src/androidTest/java/com/hoggamers/rankforge/data/tournament/RoomTournamentRepositoryTest.kt`

No new test files are required.

No production files are approved for modification.

---

## 3. Integration-Test Standard

A v0.12.3 test must prove interaction across meaningful production boundaries.

Appropriate examples include:

- use case + repository + Room
- validation + persistence
- finalization + persistence + reload
- OCR correction + finalized result + persisted evidence
- complete roster workflow continuation after reopen

Do not add tests that simply combine several isolated unit assertions into one method.

Do not duplicate already-complete unit, DAO, or repository tests.

---

## 4. Existing Integration Coverage Considered Complete

No material new v0.12.3 coverage is currently required for the following areas.

### Scoring / Standings

Existing tests already prove:

- finalized matches feed standings
- position and kill points combine correctly
- multiple finalized matches aggregate
- draft matches remain excluded
- corrections change standings
- corrected standings survive Room reopen

Do not add scoring-engine integration tests solely to increase test count.

### Persistence / Restart

Existing Room tests already prove:

- roster reload after database reopen
- match reload after reopen
- draft values survive reopen
- finalized results survive reopen
- correction drafts/history survive recreation
- normalized data survives migration/restoration
- unrelated tournament data remains isolated

Do not duplicate these persistence contracts.

### Export

Existing JVM workflow tests already prove:

- finalized match → CSV exporter → Android export coordinator
- finalized standings → CSV exporter → Android export coordinator
- export payload validity
- export identity
- blocked-state behavior

No additional export integration test is required in v0.12.3.

---

## 5. Approved Existing Test File to Modify

Modify only:

`app/src/androidTest/java/com/hoggamers/rankforge/data/tournament/RoomTournamentRepositoryTest.kt`

Add exactly the following remaining integration workflows.

---

## 6. Roster Workflow Integration

Add one continuous Room-backed production workflow proving:

1. tournament exists
2. required team slots exist
3. roster players are saved using the production roster save use case
4. roster validation runs using the production validation path
5. roster confirmation succeeds using the production confirmation use case
6. persisted state reflects the confirmed roster
7. database/repository boundary is reopened or recreated using the existing test infrastructure
8. confirmed roster reloads correctly
9. roster state remains usable after reopen

The test must use the real Room-backed repository.

Do not use an in-memory fake repository for this workflow.

Do not duplicate every `RosterValidator` unit rule.

The purpose is to prove orchestration:

`save → validate → confirm → persist → reopen → reload`

---

## 7. Match Workflow Integration

Add one continuous Room-backed production match workflow proving, using the existing APIs where applicable:

1. tournament exists
2. match is created using the production match creation path
3. placements are saved
4. kills are saved
5. match result validation succeeds
6. match is finalized using the production finalization path
7. finalized result is persisted
8. database/repository boundary is reopened or recreated
9. finalized match reloads correctly
10. workflow continuation after reopen observes the finalized state
11. finalized protection remains intact

Use production use cases over the real Room repository rather than direct repository writes wherever practical.

Do not re-test every placement/kill validation rule already covered by unit tests.

The purpose is to prove:

`create → enter results → validate → finalize → persist → reopen → continue`

---

## 8. OCR Correction Integration

Add one deterministic integration workflow using:

`FinalizeOcrCorrectionMatchUseCase`

The test should prove, using the currently implemented production path:

1. an appropriate draft/OCR-backed match state exists
2. deterministic OCR correction input is supplied
3. `FinalizeOcrCorrectionMatchUseCase` completes successfully
4. corrected/finalized match data is persisted
5. associated OCR evidence required by the current contract is persisted
6. database/repository is reopened or recreated
7. finalized/corrected result reloads correctly
8. persisted OCR evidence reloads correctly

Use sanitized deterministic OCR data.

Do not invoke genuine ML Kit screenshot recognition.

The purpose is to prove:

`OCR correction input → finalization → Room transaction → persisted result/evidence → reopen`

---

## 9. OCR Deferral Boundary

The following path remains intentionally unavailable:

`persisted OCR evidence → team matching → assignment → persisted review orchestration`

The current production architecture does not expose a complete safe persisted source/orchestration path for this sequence.

Therefore classify this as:

**BLOCKED BY EXISTING PRODUCT DEFERRAL**

v0.12.3 must not implement the missing production orchestration merely to create an integration test.

`MatchOcrReviewViewModel` receiving precomputed display input does not authorize adding new OCR/team-matching persistence behavior.

---

## 10. Genuine OCR Acceptance

Genuine screenshot accuracy is explicitly outside v0.12.3.

Do not add:

- genuine match screenshots
- genuine roster screenshots
- ML Kit accuracy measurement
- OCR accuracy thresholds
- representative production screenshot datasets

These belong to:

**v0.12.8 — OCR Acceptance Testing**

and the separately tracked real roster OCR acceptance extension.

---

## 11. Export Deferral Boundary

Do not add integration tests requiring product functionality that does not yet exist.

Deferred areas remain:

- real Android file save
- real Android share-sheet workflow
- real Android CSV filesystem integration
- Android Google Sheets client
- Android Google OAuth
- direct Google API integration
- production Google credentials/configuration

Existing deterministic export orchestration coverage is sufficient for v0.12.3.

---

## 12. Production File Boundary

No production file is approved for modification.

Do not modify:

- repositories
- use cases
- ViewModels
- Room entities
- Room DAOs
- Room database
- migrations
- OCR production code
- scoring code
- export code
- Supabase code
- Gradle configuration

Production code may be read only as necessary to construct the approved integration tests.

If an integration workflow cannot be tested without modifying production code, stop and report:

1. exact production file
2. exact missing integration seam
3. whether the issue is:
   - production defect
   - undefined contract
   - existing product deferral
   - testability limitation

Do not make the production change under the current approval.

---

## 13. Approved Implementation File Boundary

### Existing files to modify

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/tournament/RoomTournamentRepositoryTest.kt
```

### New files to create

```text
None.
```

### Production files

```text
None.
```

This is the complete planned v0.12.3 implementation boundary.

If implementation requires another existing file, stop before editing it.

---

## 14. Out of Scope

v0.12.3 does not include:

- additional scoring unit tests
- additional Room DAO tests
- Room migration tests
- backend pgTAP tests
- RLS testing
- Supabase synchronization testing
- Compose UI journeys
- navigation UI tests
- device matrix testing
- network-loss testing
- general offline recovery testing
- security audit
- genuine OCR acceptance
- regression defect cataloguing
- real CSV save/share
- Google Sheets Android integration
- Google OAuth/API implementation
- production feature development
- unrelated refactoring

These belong to other Phase 12 versions or existing deferrals.

---

## 15. Verification Policy

Because the implementation modifies only:

`src/androidTest`

required verification is instrumentation-focused.

First build the Android test APK:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Then run focused connected instrumentation for:

`RoomTournamentRepositoryTest`

using the appropriate Gradle instrumentation filtering supported by the current project.

After the focused test passes, run the complete connected instrumentation suite:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Finally:

```powershell
git diff --check
git diff --name-only
```

No Supabase, Docker, Deno, or backend verification is required.

JVM `testDebugUnitTest` is not mandatory unless implementation unexpectedly modifies JVM test files, which is not approved.

---

## 16. Completion Criteria

v0.12.3 is complete when:

1. this decision document is merged
2. implementation begins from synchronized `main`
3. only `RoomTournamentRepositoryTest.kt` is modified
4. Room-backed roster save/validate/confirm/reopen workflow is tested
5. Room-backed match create/result/validate/finalize/reopen workflow is tested
6. deterministic OCR correction finalization and evidence reopen workflow is tested
7. no production file is modified
8. blocked OCR/team-matching orchestration remains deferred
9. genuine OCR acceptance remains deferred
10. deferred real Android export integrations remain deferred
11. Android test APK compiles
12. focused `RoomTournamentRepositoryTest` instrumentation passes
13. complete connected instrumentation suite passes
14. `git diff --check` passes
15. implementation is merged through a PR
16. local `main` is synchronized with `origin/main`
17. working tree is clean

---

## 17. Decision Summary

Approved implementation:

```text
v0.12.3 — Integration Tests

1 existing instrumentation test file modified
0 new test files
0 production files

Roster:
save → validate → confirm → reopen → reload

Match:
create → results → validate → finalize → reopen → continue

OCR correction:
correction input → finalize → persist result/evidence → reopen

Scoring integration already complete
Persistence/restart integration already complete
Export orchestration integration already complete

Persisted OCR/team-matching orchestration remains deferred
Genuine OCR acceptance remains deferred
Real Android CSV/Google integration remains deferred
```

After this document is reviewed and merged, implementation may proceed on:

`feature/v0.12.3-integration-tests`
