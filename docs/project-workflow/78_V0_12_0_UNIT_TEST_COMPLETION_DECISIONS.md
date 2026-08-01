# Phase 12 v0.12.0 — Unit Test Completion Decisions

## Status

**Approved for implementation after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.0 — Unit Test Completion**

## Purpose

Complete permanent JVM unit-test coverage for deterministic Rank Forge behavior that is already implemented and approved.

The canonical v0.12.0 scope covers:

- scoring
- validation
- normalization
- player-name similarity matching
- team candidate scoring
- top-three candidate suggestions
- confidence classification
- assignment safety
- deterministic OCR parsing
- deterministic OCR validation
- deterministic OCR failure classification
- related deterministic tournament and roster domain contracts

This is primarily a **test-completion version**.

It must verify the existing production contracts without redesigning, broadening, or silently correcting them.

---

## 1. Core Decision

v0.12.0 is a **test-only implementation version**.

No production source file is approved for modification as part of the planned implementation.

The expected implementation boundary is:

- modify 10 existing JVM unit-test files
- create 2 new JVM unit-test files
- modify no production Kotlin files
- modify no Android instrumentation tests
- modify no Room/database files
- modify no Supabase/backend files
- modify no Gradle configuration

If test implementation exposes an actual production defect or an unresolved production contract, implementation must stop and report the conflict.

The defect must not be silently fixed inside v0.12.0 without a separate explicit decision.

---

## 2. Existing Production Behavior Remains Authoritative

The purpose of this version is to protect existing approved behavior.

Tests must not redefine:

- position scoring
- kill scoring
- match total calculations
- cumulative standings
- tie-break rules
- roster player-count requirements
- match validation rules
- normalization rules
- Damerau-Levenshtein behavior
- player similarity calculations
- candidate-scoring formulas
- top-three suggestion ordering
- confidence thresholds
- assignment-safety rules
- OCR parsing rules
- OCR layout rules
- OCR validation rules
- OCR failure classification
- finalized-data protection
- correction behavior

Where a current production contract appears unusual, tests should document the current approved behavior unless a separate defect decision is made.

---

## 3. Test Quality Principle

v0.12.0 must complete meaningful permanent regression coverage.

It must not add tests merely to increase test count.

A new test is justified only when it protects:

- an uncovered production branch
- a boundary value
- a failure state
- a deterministic ordering rule
- a duplicate-prevention rule
- a null/blank contract
- a threshold
- a constructor invariant
- a repository rejection path
- an exact aggregation rule
- a deterministic fallback
- another material approved behavior not already adequately protected

Existing tests that already prove a contract must not be duplicated unnecessarily.

---

## 4. Existing Coverage Considered Complete

The v0.12.0 inventory determined that the following production areas already have adequate meaningful JVM coverage.

No additional tests are currently required solely for v0.12.0 unless implementation inspection proves a concrete missing branch.

### Scoring

- `PositionPointsEngine`
- `KillPointsEngine`
- `MatchTotalEngine`
- `TieBreakRules`
- `ScoringVerificationEngine`

### Tournament / Match Domain

- `TeamSlot`
- `SaveTeamSlotNamesUseCase`
- `SaveMatchPlacementsUseCase`
- `SaveMatchKillsUseCase`
- `FinalizeOcrCorrectionMatchUseCase`
- existing match correction use cases

### Matching

- `PlayerNameComparisonNormalizer`
- `PlayerNameSimilarityMatcher`
- `TeamCandidateScorer`
- `TopTeamCandidateSuggestionProvider`
- `TeamMatchConfidenceTierClassifier`
- `TeamAssignmentSafetyEvaluator`

### Match OCR Parsing

- `PlacementParser`
- `PlayerNameParser`
- `KillParser`

### Roster OCR

- `RosterCandidateParser`
- `RosterSlotAssociator`
- `RosterOcrValidator`

### Layout / Geometry Utilities

Existing deterministic layout rectangle, coordinate-mapping, normalized-bound, and pixel-conversion behavior that already has direct adequate JVM coverage does not require duplicate testing.

---

## 5. Approved Existing Test Files to Modify

Implementation is approved to modify only the following existing JVM test files.

### 1. Cumulative Tournament Standings

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/CumulativeTournamentStandingsTest.kt
```

Add coverage for:

- empty finalized-match input
- placement present with missing confirmed kill data according to the existing engine contract
- deterministic behavior when match numbers are equal

Existing finalized filtering, aggregation, deduplication, first-place counts, latest-placement behavior, and match-cap behavior must remain unchanged.

---

### 2. Roster Validation

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/RosterValidatorTest.kt
```

Add explicit acceptance tests for:

- exactly 4 players
- exactly 6 players

These tests protect the existing approved player-count boundary.

The permitted range remains the existing 4–6 player contract.

---

### 3. Save Roster Use Case

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/SaveRosterUseCaseTest.kt
```

Add coverage for:

- tournament identity mismatch rejection
- slot identity mismatch rejection
- valid six-player persistence

Do not change repository or roster persistence behavior.

---

### 4. Confirm Tournament Roster

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ConfirmTournamentRosterUseCaseTest.kt
```

Add coverage for:

- tournament not found
- repository confirmation returning false

Existing valid confirmation and already-confirmed behavior must remain unchanged.

---

### 5. Match Result Validation

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ValidateMatchResultUseCaseTest.kt
```

Add direct stored-`Match` coverage for:

- missing typed result data
- invalid typed result data

Do not add a new production validation error for currently ignored out-of-range team-slot rows as part of this version.

---

### 6. Finalize Match

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/FinalizeMatchUseCaseTest.kt
```

Add coverage for:

- missing match
- repository finalization rejection

Existing validation gating and finalized-state behavior must remain unchanged.

---

### 7. Free Fire MAX Scoreboard Layout

```text
app/src/test/java/com/hoggamers/rankforge/domain/ocr/layout/FreeFireMaxScoreboardLayoutTest.kt
```

Add coverage for:

- invalid dimensions
- exact supported aspect-ratio boundaries

Do not change the current scoreboard calibration or supported layout rules.

---

### 8. Cropped Roster Panel Layout

```text
app/src/test/java/com/hoggamers/rankforge/domain/ocr/layout/CroppedRosterPanelLayoutTest.kt
```

Add direct coverage for the remaining validator branches, including where applicable:

- null screenshot position
- invalid slot structure
- overlapping layout regions
- invalid slot-number association
- row-containment failures
- row-index validation branches

Tests must use the existing layout contracts.

Do not redefine crop geometry or roster-layout rules.

---

### 9. OCR Failure Analyzer

```text
app/src/test/java/com/hoggamers/rankforge/domain/ocr/review/OcrFailureAnalyzerTest.kt
```

Add coverage for:

- raw OCR extraction failure
- parser-output-unavailable fallback

Existing OCR uncertainty/failure classification remains authoritative.

---

### 10. Real Screenshot Evaluator

```text
app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/RealScreenshotEvaluatorTest.kt
```

Add deterministic evaluator coverage for:

- missing parser results
- duplicate expected IDs
- out-of-range expected IDs
- metric denominator boundaries
- coverage metric boundaries

This is deterministic evaluator testing only.

It does not authorize genuine screenshot acceptance testing.

---

## 6. Approved New JVM Test Files

Only the following two new JVM test files are planned.

### 1. RosterPlayerTest

Create:

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/RosterPlayerTest.kt
```

Purpose:

Directly protect `RosterPlayer` constructor/factory invariants.

Required coverage:

- valid construction
- blank tournament ID rejection
- slot 1 accepted
- slot 12 accepted
- slot 0 rejected
- slot 13 rejected

Do not change the production invariant.

---

### 2. ValidateTournamentRosterUseCaseTest

Create:

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ValidateTournamentRosterUseCaseTest.kt
```

Purpose:

Provide direct coverage for behavior currently exercised only indirectly through tournament confirmation.

Required coverage:

- repository roster aggregation
- deterministic slot ordering
- team-name lookup/association
- `teamNamesBySlotNumber` override behavior
- existing validation output forwarding

Do not duplicate `RosterValidator` internals inside the use-case test.

---

## 7. Frozen Decision — Out-of-Range Match Team Slots

The inventory identified that `ValidateMatchResultUseCase` currently ignores certain out-of-range `teamSlotNumber` rows instead of producing a dedicated validation error.

v0.12.0 must **not change that production behavior**.

Reason:

- no current corresponding validation-error contract exists
- introducing one would be a production behavior change
- Phase 12 QA must not silently redefine an existing domain contract

For v0.12.0:

- test existing defined validation behavior
- do not invent a new validation error
- do not modify `ValidateMatchResultUseCase`
- record the behavior for separate defect/contract review if it is later considered incorrect

This does not block v0.12.0.

---

## 8. Frozen Decision — RealScreenshotEvaluator Fixtures

`RealScreenshotEvaluator` may be tested in v0.12.0 using deterministic sanitized parsed observations.

A new genuine-image fixture set is not required for this version.

v0.12.0 tests should verify only the evaluator's deterministic calculations and validation behavior after parsed observations are supplied.

They must not execute:

- ML Kit against real screenshots
- Android image decoding
- actual screenshot preprocessing
- genuine scoreboard acceptance
- genuine roster screenshot acceptance

Those belong to later acceptance testing.

---

## 9. Frozen Decision — Genuine Screenshot Acceptance

Genuine screenshot acceptance testing is explicitly excluded from v0.12.0.

Canonical genuine match-OCR acceptance remains:

**v0.12.8 — OCR Acceptance Testing**

The separate roadmap extension for real roster OCR acceptance also remains outside v0.12.0.

Therefore v0.12.0 must not:

- add genuine screenshot datasets merely to satisfy unit testing
- define new OCR accuracy thresholds
- claim production OCR accuracy
- perform real-device screenshot OCR acceptance
- conflate deterministic evaluator tests with real OCR acceptance

---

## 10. Production File Boundary

No production source files are approved for planned modification.

The implementation must not modify files under:

```text
app/src/main/
```

If any proposed JVM test cannot be written without changing production code, implementation must stop and report:

1. the exact production file required
2. why the existing contract cannot be tested safely
3. whether the issue appears to be:
   - a testability limitation
   - an undefined contract
   - or a genuine production defect

No production change may then proceed without explicit review.

---

## 11. Out of Scope

v0.12.0 must not modify or implement:

- Room entities
- Room DAOs
- Room database version
- Room migrations
- Room transaction behavior
- Supabase migrations
- Supabase schema
- RLS
- RPCs
- Edge Functions
- authentication
- authorization
- sync queue behavior
- retry behavior
- conflict-resolution behavior
- idempotency algorithms
- Compose screens
- ViewModels
- navigation
- Android instrumentation tests
- emulator compatibility tests
- physical-device compatibility tests
- offline simulation
- connectivity-loss testing
- genuine match screenshots
- genuine roster screenshots
- OCR accuracy thresholds
- Android CSV save/share infrastructure
- Google Sheets Android integration
- production Google configuration
- CI configuration
- dependency upgrades
- unrelated refactoring

These concerns belong to later Phase 12 versions or documented later-phase work.

---

## 12. Relationship to Later Phase 12 Versions

v0.12.0 must remain separated from later QA layers.

### v0.12.1 — Database Tests

Owns:

- Room operations
- Room transactions
- Room migrations
- persistence integrity
- database constraints

### v0.12.2 — Backend Tests

Owns:

- Supabase schema
- RLS
- authorization
- synchronization
- backend idempotency

### v0.12.3 — Integration Tests

Owns:

- multi-component complete workflows
- roster-to-match integration
- OCR-to-review integration
- scoring/standings integration
- persistence/restart integration
- export workflow integration

### v0.12.4 — Compose UI Tests

Owns:

- navigation UI behavior
- data-entry UI
- correction screens
- validation screens
- finalization screens

### v0.12.5 — Device Compatibility

Owns:

- API 26
- target API
- emulator matrix
- physical-device compatibility

### v0.12.6 — Offline and Recovery Testing

Owns:

- connectivity loss
- interrupted synchronization
- restart recovery
- retry behavior

### v0.12.7 — Security Review

Owns:

- credentials
- secrets
- RLS
- authorization
- storage policies
- repository hygiene

### v0.12.8 — OCR Acceptance Testing

Owns:

- approved genuine match screenshot set
- reproducible match-OCR accuracy evaluation

### v0.12.9 — Regression Test Suite

Owns:

- permanent coverage review for historically fixed defects

The separate roster OCR genuine acceptance roadmap extension also remains outside v0.12.0.

---

## 13. Non-Regression Requirements

v0.12.0 must preserve all completed behavior, including:

- tournament creation
- tournament details
- roster creation
- roster validation
- roster confirmation
- manual match creation
- placement entry
- kill entry
- match result validation
- match review
- finalization
- protected corrections
- scoring
- cumulative standings
- tie-break ordering
- local persistence
- authentication
- cloud synchronization
- offline queue behavior
- idempotency
- conflict resolution
- screenshot handling
- match OCR
- roster OCR
- name normalization
- player similarity
- team candidate scoring
- top-three suggestions
- confidence classification
- assignment safety
- OCR correction
- CSV export
- Google Sheets backend export
- Phase 11 workflow integration
- finalized-data protection

Tests must describe and protect these contracts rather than change them.

---

## 14. Approved Implementation File Boundary

### Existing files to modify

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/CumulativeTournamentStandingsTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/RosterValidatorTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/SaveRosterUseCaseTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ConfirmTournamentRosterUseCaseTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ValidateMatchResultUseCaseTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/FinalizeMatchUseCaseTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/ocr/layout/FreeFireMaxScoreboardLayoutTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/ocr/layout/CroppedRosterPanelLayoutTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/ocr/review/OcrFailureAnalyzerTest.kt
app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/RealScreenshotEvaluatorTest.kt
```

### New files to create

```text
app/src/test/java/com/hoggamers/rankforge/domain/tournament/RosterPlayerTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/tournament/ValidateTournamentRosterUseCaseTest.kt
```

### Production files

```text
None.
```

This is the approved planned implementation boundary.

If implementation discovers a required file outside this list, stop before editing it and review the boundary.

---

## 15. Implementation Strategy

Implementation should be performed manually unless the final test changes become unexpectedly complex.

The preferred sequence is:

1. update the smaller tournament/roster boundary tests
2. create `RosterPlayerTest`
3. create `ValidateTournamentRosterUseCaseTest`
4. update match validation/finalization tests
5. update standings tests
6. update OCR layout tests
7. update OCR failure tests
8. update evaluator tests
9. run focused JVM verification
10. run complete JVM unit-test verification
11. inspect the final file boundary

No broad repository implementation agent is required for the planned work.

---

## 16. Verification Policy

Implementation verification should be phased.

### First — focused JVM tests

Run only the directly modified/created test classes first.

### Second — complete JVM suite

After focused tests pass:

```powershell
.\gradlew.bat testDebugUnitTest
```

### Third — build safety

Because production files are not expected to change, broader Android verification should be proportional.

At minimum after successful JVM testing:

```powershell
.\gradlew.bat assembleDebug
git diff --check
```

`assembleDebugAndroidTest` or connected device tests are not automatically required solely because JVM test files changed.

If implementation unexpectedly affects Android/instrumented compilation, investigate before expanding verification.

### Repository verification

Before PR creation verify:

```powershell
git status --short
git diff --name-only
git diff --check
```

The changed implementation files must remain within the approved 12-test-file boundary.

---

## 17. Completion Criteria

v0.12.0 is complete when:

1. this decision document has been merged into `main`
2. the implementation branch is created from synchronized `main`
3. only approved JVM test files are changed
4. the 10 approved existing tests receive the required missing coverage
5. the 2 approved new JVM test files are added
6. no production source file is modified
7. focused JVM tests pass
8. complete `testDebugUnitTest` passes
9. `assembleDebug` passes
10. `git diff --check` passes
11. final changed-file boundary is reviewed
12. implementation is merged through a pull request
13. local `main` is synchronized with `origin/main`
14. the working tree is clean

---

## 18. Decision Summary

The approved v0.12.0 implementation is:

```text
Test-only JVM coverage completion
10 existing test files modified
2 new test files created
0 production files modified
0 Room changes
0 Supabase changes
0 Compose changes
0 instrumentation changes
0 genuine screenshot acceptance work
```

The current production contracts remain authoritative.

Any genuine defect discovered while completing these tests must be reported separately rather than silently repaired inside the approved test-only boundary.

---

## 19. Implementation Readiness

The v0.12.0 scope, test boundary, unresolved-contract handling, and acceptance requirements are now frozen.

After this document is:

1. reviewed
2. committed
3. pushed
4. merged through a documentation-only pull request
5. and local `main` is synchronized again

the project may create:

```text
feature/v0.12.0-unit-test-completion
```

and implement only the approved JVM test changes.
