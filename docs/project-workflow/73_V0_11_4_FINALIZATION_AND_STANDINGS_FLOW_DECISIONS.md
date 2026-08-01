# Phase 11 v0.11.4 — Finalization and Standings Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.4 — Finalization and Standings Flow

## Purpose

Connect valid match finalization to immediate tournament standings refresh.

This version integrates already implemented manual/OCR-assisted match review, finalization safeguards, scoring, cumulative standings, tie-break ordering, persistence, and finalized-data protection into one reliable finalization workflow.

The canonical outcome is:

```text
Draft Match Review / Corrected OCR Review
-> Validate match result
-> Finalize valid match
-> Persist finalized match state
-> Recalculate tournament standings immediately
-> Display updated tournament standings/details for the same tournament
```

This version is workflow integration only.

It must not redefine scoring rules, standings ordering, tie-break behavior, match validation rules, finalized protection rules, correction behavior, cloud sync, export, or database schema.

## Current Context

Phase 11 v0.11.0 is complete.

Tournament creation enters setup directly, preserves the exact persisted tournament ID, and returns successful roster confirmation to the same tournament details screen.

Phase 11 v0.11.1 is complete.

Manual match creation routes through placement entry, kill entry, match review, and return-to-details while preserving the exact tournament ID and match ID.

Phase 11 v0.11.2 is complete.

Match Review routes safely to OCR Review for linked draft screenshots, preserves exact tournament ID and match ID, and returns OCR Review back to the same Match Review context.

Phase 11 v0.11.3 is complete.

OCR Review safely loads existing precomputed OCR/team-matching evidence when available, preserves exact tournament ID and match ID, keeps empty OCR state unchanged, avoids fake matching results, and preserves manual review behavior.

v0.11.4 starts after those workflow integrations.

Earlier versions already implemented the pieces this version must connect:

- manual match result entry
- match review
- match validation
- scoring engine
- cumulative standings
- tie-break ordering
- finalized match state
- finalized-data protection
- protected correction workflow
- local Room persistence
- cloud sync primitives
- export primitives

This version must connect existing pieces. It must not re-implement them.

## In Scope

v0.11.4 includes only finalization-to-standings workflow integration.

In scope:

1. Finalize a valid draft match from the existing Match Review path.
2. Preserve the exact tournament ID and match ID during finalization.
3. Use existing match validation before finalization.
4. Use existing scoring rules for position points, kill points, and match totals.
5. Use existing cumulative standings logic after finalization.
6. Use existing tie-break behavior after standings recalculation.
7. Refresh or expose updated tournament standings immediately after successful finalization.
8. Return or navigate to the same tournament context after successful finalization.
9. Keep finalized match protection intact.
10. Keep protected correction behavior intact.
11. Prevent invalid, incomplete, duplicate, or unsafe match results from finalizing.
12. Preserve manual match flow from v0.11.1.
13. Preserve screenshot/OCR review flow from v0.11.2.
14. Preserve team-matching advisory behavior from v0.11.3.
15. Add or update focused tests for finalization and standings refresh.

## Out of Scope

This version must not implement or modify:

- position scoring rules
- kill scoring rules
- match total formula
- cumulative standings formula
- tie-break rules
- validation rules for placements, kills, teams, or duplicate rows
- Room schema, migrations, entities, DAOs, or database version
- Supabase schema, RLS, RPCs, edge functions, or cloud upload behavior
- offline sync queue behavior
- cloud conflict resolution behavior
- protected correction domain rules
- OCR extraction
- OCR parsing
- OCR preprocessing
- screenshot linking
- screenshot validation
- duplicate screenshot detection
- local image preservation
- team matching algorithms
- team-matching confidence thresholds
- automatic OCR/team assignment behavior
- export workflow
- CSV save/share workflow
- Google Sheets workflow
- authentication behavior
- tournament setup behavior
- roster setup behavior
- visual redesign
- new analytics
- new notifications
- production deployment

## Canonical Flow

### 1. Starting Context

Finalization starts from an existing reviewed match context.

Accepted entry contexts:

```text
MatchReviewDestination(tournamentId, matchId)
```

or an existing OCR/correction flow that returns to the same reviewed match identity before finalization.

The implementation must preserve:

```text
tournamentId
matchId
```

The implementation must not infer the target match from:

- match number alone
- tournament list position
- visible row order
- screenshot filename
- OCR text
- temporary UI state alone
- stale route arguments
- previously selected match memory

### 2. Pre-Finalization Validation

Before finalization, the workflow must require existing validation to pass.

A match must not finalize if it has:

- missing placement rows
- duplicate placements
- invalid placement values
- missing team assignments
- duplicate team assignments
- invalid team slots
- missing kill values
- negative kill values
- incomplete result rows
- unsafe OCR/team-matching state that still requires manual review
- any existing validation failure exposed by the current domain/use-case layer

The implementation must use the current validation path. It must not create a parallel validation system inside UI or navigation code.

### 3. Finalization

Successful finalization must use the existing finalization mechanism.

Finalization must:

- target the exact match ID
- target the exact tournament ID
- persist the finalized state
- protect the match from normal draft edits after finalization
- preserve existing protected correction entry points
- avoid duplicate finalization side effects when the same finalized match is revisited

This version must not add a second finalization state machine.

### 4. Standings Refresh

After a match finalizes successfully, tournament standings must update immediately from authoritative finalized match data.

The standings refresh must:

- include finalized matches only
- exclude draft matches
- use existing scoring logic
- use existing cumulative standings logic
- use existing tie-break ordering
- preserve exact team slots and team names
- handle repeated finalization/reopen of the same screen without duplicate points
- show or expose updated standings for the same tournament context

The workflow must not compute standings from raw OCR evidence.

The workflow must not compute standings from unconfirmed team-matching suggestions.

The workflow must not compute standings from stale draft rows after finalization succeeds.

### 5. Return Behavior

After successful finalization, the preferred destination is the same tournament context with updated standings visible or available.

Preferred return target:

```text
TournamentDetailsDestination(tournamentId)
```

Acceptable same-context alternatives:

```text
MatchReviewDestination(tournamentId, matchId)
```

only if the screen clearly shows finalized state and updated standings can be reached without losing the tournament context.

The workflow must not return to tournament list unless no safe same-tournament destination exists.

### 6. Failure Behavior

If finalization fails, the user must remain in the same match review context.

Failure must not:

- partially finalize the match
- update standings from invalid data
- navigate away from the match context
- silently ignore validation errors
- hide existing correction/manual review actions
- overwrite correction drafts
- mutate protected finalized data

Existing error surfaces should be reused.

## Decisions

### Decision 1 — Existing scoring and standings logic remains authoritative

v0.11.4 must use existing scoring, cumulative standings, and tie-break components.

It must not modify scoring constants, formulas, tie-break ordering, or standings aggregation behavior.

### Decision 2 — Finalization must be validation-gated

Only valid match results may finalize.

The implementation must use existing validation. UI code must not bypass domain validation.

### Decision 3 — Standings update immediately after successful finalization

A successful finalization must refresh or expose updated tournament standings immediately for the same tournament.

The user should not need to restart the app, reopen the tournament, or manually trigger a recalculation to see the updated standings.

### Decision 4 — Finalized matches remain protected

After finalization, normal draft edit paths must remain blocked.

Any post-finalization modification must continue to use the existing protected correction workflow.

### Decision 5 — Draft matches do not affect standings

Draft, incomplete, invalid, or unfinalized matches must not contribute to tournament standings.

### Decision 6 — OCR/team-matching evidence is not standings input

Standings must use finalized application match data only.

Raw OCR evidence, advisory suggestions, and unconfirmed matching results must not directly affect standings.

### Decision 7 — No cloud sync or export workflow

v0.11.4 is local app workflow integration.

Cloud synchronization remains v0.11.5.

CSV and Google Sheets export workflow remains v0.11.6.

### Decision 8 — Exact identity preservation is mandatory

Finalization and standings refresh must preserve the exact tournament ID and match ID.

No finalized result may be applied to another match or tournament.

## Acceptance Criteria

v0.11.4 is accepted when all of the following are true:

1. A valid draft match can be finalized from the existing Match Review workflow.
2. The exact tournament ID and match ID are preserved through finalization.
3. Existing match validation blocks invalid finalization.
4. Missing placements block finalization.
5. Duplicate placements block finalization.
6. Missing or duplicate team assignments block finalization.
7. Invalid or negative kills block finalization.
8. Existing scoring logic calculates match totals.
9. Existing cumulative standings logic updates tournament standings.
10. Existing tie-break behavior is preserved.
11. Updated standings are visible or available immediately after finalization.
12. Draft matches do not affect standings.
13. Reopening a finalized match does not duplicate standings points.
14. Finalized matches remain protected from ordinary draft edits.
15. Protected correction behavior remains available and unchanged.
16. OCR/team-matching evidence does not directly affect standings before confirmation/finalization.
17. v0.11.1 manual match flow still works.
18. v0.11.2 screenshot/OCR review flow still works.
19. v0.11.3 team-matching review state remains advisory and safe.
20. No Room schema, Supabase, cloud sync, export, OCR parser, screenshot handling, matching algorithm, Gradle, schema, or visual redesign changes are introduced unless implementation proves a current compile-blocking issue inside the approved boundary.

## Required Test Coverage

Implementation should add or update focused tests for finalization-to-standings workflow integration.

### Unit tests

Where ViewModels/use-case callers expose finalization behavior, test:

- valid match finalization preserves exact tournament ID and match ID
- invalid match cannot finalize
- incomplete placement data cannot finalize
- duplicate placement data cannot finalize
- missing team assignment cannot finalize
- duplicate team assignment cannot finalize
- negative kills cannot finalize
- successful finalization updates match status to finalized
- successful finalization refreshes or exposes updated standings
- draft matches are excluded from standings
- finalized match is not counted twice after reload/re-entry
- existing scoring result is used rather than recalculated differently in UI
- existing standings ordering/tie-break result is preserved
- finalization failure leaves user in same match review context
- protected correction entry remains available for finalized matches
- ordinary draft edit actions are blocked or hidden for finalized matches where existing behavior requires that

### Screen tests

Where finalization is triggered or displayed, test:

- finalize action is visible only when appropriate
- validation errors are visible when finalization is blocked
- successful finalization shows finalized status
- successful finalization gives access to updated standings/tournament details
- finalized match does not expose ordinary draft edit actions
- protected correction action remains available where existing behavior supports it
- standings display updates after finalization if the screen owns standings display

### Navigation / instrumentation tests

Add or update connected navigation coverage for:

```text
confirmed tournament details
-> create draft match
-> placement entry
-> kill entry
-> match review
-> finalize valid match
-> same tournament details / standings context
```

Also cover:

- same tournament ID is preserved
- same match ID is finalized
- tournament details remain reachable
- updated standings are visible or available after finalization
- finalized match cannot be edited through ordinary draft path
- existing v0.11.2 OCR review navigation still works
- no Hilt fallback is introduced in non-Hilt navigation tests

Use existing fake/in-memory repositories, existing test ViewModel factories, and existing navigation-test wiring.

Do not introduce Hilt test infrastructure unless already required by existing tests.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

- Match Review ViewModel/state
- Match Review Screen finalization action/state
- Tournament Details ViewModel/state standings refresh
- Tournament Details Screen standings display refresh if needed
- navigation return behavior after successful finalization
- focused unit tests
- focused screen tests
- focused navigation tests

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

Targeted tests may be run first if implementation primarily changes Match Review, Tournament Details, and navigation.

## Non-Regression Requirements

The implementation must preserve:

- v0.11.0 tournament setup flow
- v0.11.1 manual match flow
- v0.11.2 screenshot/OCR review flow
- v0.11.3 team-matching flow
- tournament creation direct setup navigation
- roster confirmation return-to-details navigation
- existing tournament list/details behavior
- existing match creation validation
- existing placement validation
- existing kill validation
- existing match review validation display
- existing finalized match protection behavior
- existing protected correction workflow
- existing OCR review route behavior
- existing OCR empty-state behavior
- existing screenshot validation behavior
- existing duplicate detection behavior
- existing local image preservation behavior
- existing matching advisory behavior
- existing confidence/safety behavior
- existing scoring behavior
- existing standings behavior
- existing tie-break behavior
- existing local persistence behavior
- existing cloud sync route behavior
- existing export behavior

## Completion Definition

v0.11.4 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Valid match finalization is connected to immediate standings refresh.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
