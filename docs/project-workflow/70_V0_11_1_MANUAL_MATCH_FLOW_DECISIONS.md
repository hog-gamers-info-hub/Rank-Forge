# Phase 11 v0.11.1 — Manual Match Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.1 — Manual Match Flow

## Purpose

Connect the already implemented manual match screens into one reliable end-to-end draft match workflow.

This version must make the manual match path feel continuous from a confirmed tournament details screen:

```text
Tournament Details
-> Match Creation
-> Manual Placement Entry
-> Manual Kill Entry
-> Match Review
-> Tournament Details
```

The purpose of this version is workflow integration only. Existing domain rules, persistence behavior, validation rules, and draft handling must remain authoritative.

## Current Context

Phase 11 v0.11.0 is complete.

The tournament setup flow now carries the exact persisted tournament ID from creation into setup, enters team setup directly after creation, and returns successful roster confirmation to the same tournament details screen.

v0.11.1 starts after that setup flow. It connects the manual match workflow for tournaments that are ready for match entry.

Earlier phases already introduced the individual match workflow pieces:

* match creation
* manual placement entry
* manual kill entry
* match review
* draft match values
* validation surfacing
* finalized match protection foundations

This version must connect those pieces cleanly without re-implementing their domain logic.

## In Scope

v0.11.1 includes only manual draft match workflow integration.

In scope:

1. Start manual match creation from the correct tournament details context.
2. Preserve the exact tournament ID through the full manual match flow.
3. Preserve the exact created match ID through placement, kill, and review screens.
4. Route successful match creation directly into manual placement entry for the created match.
5. Route successful placement save into manual kill entry for the same match.
6. Route successful kill save into match review for the same match.
7. Allow Match Review edit actions to return to placement entry and kill entry for the same match.
8. Allow Match Review details/back action to return to the same tournament details screen.
9. Keep draft values recoverable through the existing draft/persistence mechanisms.
10. Keep existing validation and missing-result messages authoritative.
11. Keep match finalization out of this version except where existing review UI already displays existing controls.
12. Add or update focused tests for the connected manual flow.

## Out of Scope

This version must not implement or modify:

* OCR screenshot processing flow
* OCR review routing
* team matching flow
* automatic team assignment
* roster OCR workflow
* match finalization workflow as a new integration step
* standings recalculation workflow
* cloud synchronization workflow
* export workflow
* Supabase schema, RLS, RPCs, edge functions, or cloud upload behavior
* Room schema, migrations, entities, DAOs, or database version
* scoring engine logic
* tie-break logic
* protected correction logic
* tournament creation/setup flow beyond consuming the state produced by v0.11.0
* roster confirmation behavior
* authentication behavior
* visual redesign

## Canonical Flow

### 1. Entry Point

Manual match flow starts from an existing tournament details screen.

The expected starting context is:

```text
TournamentDetailsDestination(tournamentId)
```

The tournament ID must be preserved exactly.

The flow must not create a new tournament, infer a tournament, or rely on list position.

### 2. Match Creation

When the user selects the create-match action from tournament details, navigation should open:

```text
MatchCreationDestination(tournamentId)
```

The match creation screen must receive the same tournament ID.

After successful match creation, the workflow should immediately continue to placement entry for the created match:

```text
MatchPlacementDestination(tournamentId, matchId)
```

The `matchId` must come from the successfully persisted match result.

The implementation must not synthesize a match ID in the UI layer.

### 3. Manual Placement Entry

Placement entry must operate on the exact tournament ID and match ID received from successful match creation.

After a successful placement save, navigation should continue to kill entry:

```text
MatchKillDestination(tournamentId, matchId)
```

Placement validation and persistence must remain handled by the existing placement ViewModel/use cases.

This version must not duplicate placement validation rules in navigation.

### 4. Manual Kill Entry

Kill entry must operate on the exact same tournament ID and match ID.

After a successful kill save, navigation should continue to match review:

```text
MatchReviewDestination(tournamentId, matchId)
```

Kill validation and persistence must remain handled by the existing kill ViewModel/use cases.

This version must not duplicate kill validation rules in navigation.

### 5. Match Review

Match review must load and display the same tournament ID and match ID.

From match review:

* placement edit action must navigate to `MatchPlacementDestination(tournamentId, matchId)`
* kill edit action must navigate to `MatchKillDestination(tournamentId, matchId)`
* details/back action must return to `TournamentDetailsDestination(tournamentId)`

The flow must not lose match draft data when moving between review, placement, and kill screens.

### 6. Returning to Tournament Details

The match review details/back action should return to the same tournament details screen.

Preferred behavior:

```text
popBackStack(TournamentDetailsDestination(tournamentId), inclusive = false)
```

Fallback behavior is allowed if the details destination is not already present:

```text
navigate(TournamentDetailsDestination(tournamentId))
```

The fallback must avoid creating a confusing stack when possible.

## Navigation Decisions

### Decision 1 — Match creation must carry created match ID forward

Successful match creation must emit or expose a navigation event containing the exact created match ID.

The UI/navigation layer must use the match ID returned by the match creation result.

Accepted shape:

```kotlin
Created(tournamentId: String, matchId: String)
```

or an equivalent typed one-shot navigation model.

Stringly typed temporary state is not preferred unless already used by the local architecture.

### Decision 2 — Save actions should drive forward workflow only after success

Placement entry must navigate forward only after the placement save succeeds.

Kill entry must navigate forward only after the kill save succeeds.

Validation failures, persistence failures, and incomplete draft states must stay on the current screen and surface the existing error/validation state.

### Decision 3 — Existing draft persistence remains authoritative

Existing draft match value use cases and repository behavior remain the source of truth.

This version must not create a second draft store, temporary UI cache, or alternate persistence path.

### Decision 4 — Review screen is the hub for manual corrections before finalization

For manual workflow integration, Match Review is the hub after placement and kill entry.

The user must be able to return from Match Review to placement or kill entry for the same match.

This version does not need to finalize the match as a new workflow milestone. Full finalization and standings integration belongs to v0.11.4.

### Decision 5 — Manual flow must not invoke OCR

Manual match flow must remain fully manual.

It must not invoke OCR extraction, OCR review, OCR team matching, screenshot parsing, or automatic assignment.

Screenshot/OCR processing belongs to later Phase 11 versions.

### Decision 6 — Tournament status behavior must remain existing behavior

This version must not redefine when a tournament can create matches.

If existing UI/domain rules already restrict match creation to confirmed tournaments, preserve that.

If existing tests already define visibility/enabled behavior for create-match actions, preserve those expectations.

Do not broaden match creation availability without an explicit later decision.

## Acceptance Criteria

v0.11.1 is accepted when all of the following are true:

1. From tournament details, the create-match action opens match creation for the same tournament ID.
2. Successful match creation routes directly to placement entry for the exact created match ID.
3. Successful placement save routes directly to kill entry for the same tournament ID and match ID.
4. Successful kill save routes directly to match review for the same tournament ID and match ID.
5. Match Review placement edit action routes back to placement entry for the same match.
6. Match Review kill edit action routes back to kill entry for the same match.
7. Match Review details/back action returns to the same tournament details screen.
8. Draft placement and kill values remain recoverable using existing draft mechanisms.
9. Existing validation/missing-result behavior remains visible and unchanged.
10. Existing finalized match protection behavior is not weakened.
11. No OCR, cloud sync, export, scoring, standings, schema, or Supabase behavior is introduced.
12. Tests cover the connected manual match workflow.

## Required Test Coverage

Implementation should add or update focused tests for:

### Unit tests

Where ViewModels expose navigation events, test:

* successful match creation emits navigation with exact tournament ID and match ID
* failed match creation emits no forward navigation
* successful placement save emits navigation to kill entry for the same match
* invalid placement save emits no forward navigation
* successful kill save emits navigation to review for the same match
* invalid kill save emits no forward navigation
* one-shot navigation events are consumed/cleared correctly

### Navigation/instrumentation tests

Add or update connected navigation coverage for the full manual path:

```text
confirmed tournament details
-> create match
-> placement entry
-> kill entry
-> match review
-> tournament details
```

Also cover:

* review placement edit returns to placement entry for the same match
* review kill edit returns to kill entry for the same match
* review details/back returns to the same tournament details screen

Tests should use existing in-memory repositories and existing test ViewModel factories.

Do not introduce Hilt test infrastructure unless already required by existing tests.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

* match creation UI state / ViewModel / route
* match placement UI state / ViewModel / route
* match kill UI state / ViewModel / route
* match review route integration
* navigation host
* focused unit tests
* focused navigation tests

The implementation prompt must list the exact approved file boundary before edits.

If implementation discovers that a required file is outside the approved boundary, it must stop and report the needed file instead of editing broadly.

## Verification Policy

Codex should run only lightweight verification unless explicitly instructed otherwise.

Codex-side check:

```text
git diff --check
```

Local verification outside Codex should include:

```text
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

Targeted connected navigation tests may be run first if the implementation primarily changes navigation.

## Non-Regression Requirements

The implementation must preserve:

* v0.11.0 tournament setup flow
* tournament creation direct setup navigation
* roster confirmation return-to-details navigation
* existing tournament list/details behavior
* existing match creation validation
* existing placement validation
* existing kill validation
* existing match review validation display
* existing finalized match protection behavior
* existing correction workflow entry points
* existing OCR review route behavior
* existing cloud sync route behavior
* existing export behavior

## Completion Definition

v0.11.1 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Manual match flow integration is implemented within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
