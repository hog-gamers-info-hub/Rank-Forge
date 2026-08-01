# Phase 11 v0.11.3 — Team Matching Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.3 — Team Matching Flow

## Purpose

Connect the already implemented OCR review, player-name normalization, player similarity matching, team candidate scoring, top-three suggestions, confidence threshold classification, and assignment safety rules into one reliable team-matching review flow for OCR-assisted draft matches.

This version must make the post-OCR team-matching path usable from the existing OCR review context without changing the underlying matching algorithms.

The target flow is:

```text
Tournament Details
-> Draft Match Review
-> OCR Review
-> Team Matching Review
-> Manual Confirmation / Correction Continuation
```

The purpose of this version is workflow integration only.

Existing normalization, similarity, candidate scoring, suggestion ranking, confidence classification, and assignment safety rules must remain authoritative.

## Current Context

Phase 11 v0.11.0 is complete.

Tournament creation now enters setup directly, preserves the exact persisted tournament ID, and returns successful roster confirmation to the same tournament details screen.

Phase 11 v0.11.1 is complete.

Manual match creation now routes through placement entry, kill entry, match review, and return-to-details while preserving the exact tournament ID and match ID.

Phase 11 v0.11.2 is complete.

Match Review now has safe OCR Review workflow integration, preserves exact tournament ID and match ID, returns OCR Review back to the same match review context, and preserves existing screenshot linking behavior.

v0.11.3 starts after that. It connects the team-matching decision flow after OCR review.

Earlier Phase 9 versions already implemented the individual matching pieces:

* v0.9.0 — text normalization
* v0.9.1 — player similarity matching
* v0.9.2 — team candidate scoring
* v0.9.3 — top-three suggestions
* v0.9.4 — confidence thresholds
* v0.9.5 — assignment safety rules
* v0.9.6 — OCR review interface
* v0.9.7 — manual field correction
* v0.9.8 — safe match finalization
* v0.9.9 — original and corrected data preservation

This version must connect those pieces cleanly without re-implementing them.

## In Scope

v0.11.3 includes only team-matching workflow integration for OCR-assisted draft matches.

In scope:

1. Start team matching from the correct OCR review context.
2. Preserve the exact tournament ID and match ID through team-matching review.
3. Use existing roster data for the same tournament.
4. Use existing detected OCR player-name evidence when available.
5. Use existing normalization and player similarity matching logic.
6. Use existing team candidate scoring logic.
7. Use existing top-three suggestion logic.
8. Use existing confidence threshold logic.
9. Use existing assignment safety logic.
10. Display or expose matching results in the existing OCR review / correction flow.
11. Preserve advisory evidence for user review.
12. Support manual review/correction when matching is uncertain, unsafe, or incomplete.
13. Preserve v0.11.1 manual match flow.
14. Preserve v0.11.2 screenshot/OCR review navigation.
15. Add or update focused tests for team-matching workflow integration.

## Out of Scope

This version must not implement or modify:

* normalization algorithm rules
* player similarity algorithm rules
* team candidate scoring algorithm rules
* top-three ranking algorithm rules
* confidence threshold values
* assignment safety rule definitions
* OCR extraction
* OCR parsing
* screenshot linking
* screenshot validation
* duplicate screenshot detection
* local image preservation
* roster OCR intake or parsing
* automatic finalization
* standings recalculation workflow
* export workflow
* cloud synchronization workflow
* Supabase schema, RLS, RPCs, edge functions, or cloud upload behavior
* Room schema, migrations, entities, DAOs, or database version
* scoring engine logic
* tie-break logic
* protected correction logic
* tournament setup flow
* manual match flow behavior except where team matching must coexist with OCR review
* authentication behavior
* visual redesign

## Canonical Flow

### 1. Entry Point

Team matching starts from an OCR-assisted match context.

The expected starting context is:

```text
MatchOcrReviewDestination(tournamentId, matchId)
```

or the existing OCR review screen state for the same tournament and match.

The tournament ID and match ID must be preserved exactly.

The implementation must not infer the target match from:

* match number alone
* tournament list position
* row order alone
* screenshot filename
* screenshot metadata
* OCR text alone
* visible UI ordering

### 2. Inputs

Team matching may use only existing authoritative inputs:

* tournament ID
* match ID
* OCR review row evidence
* detected player names per OCR row
* roster teams for the same tournament
* roster players for each team slot
* existing OCR review/correction draft state if already available

If OCR evidence is unavailable, incomplete, or empty, the team-matching flow must not invent detected players or fake matching results.

If roster data is unavailable or incomplete, the flow must surface the existing review/manual-required state rather than guessing.

### 3. Matching Pipeline

For each OCR row that has detected player names, the workflow should use existing components in this order:

```text
detected player names
-> PlayerNameComparisonNormalizer
-> PlayerNameSimilarityMatcher
-> TeamCandidateScorer
-> TopTeamCandidateSuggestionProvider
-> TeamMatchConfidenceTierClassifier
-> TeamAssignmentSafetyEvaluator
```

The implementation must not duplicate these algorithms inside UI, navigation, or ViewModel code.

### 4. Suggestions

For each OCR row, the team-matching flow should expose the existing top-three team suggestions when available.

Suggestions must remain advisory.

The UI or review state should make it clear when:

* there is a strong automatic candidate
* confirmation is required
* manual review is required
* no safe candidate exists
* duplicate team assignment prevents automatic use
* insufficient player matches prevent automatic use
* candidate lead is insufficient
* roster data is missing or incomplete

### 5. Safety

Automatic team assignment must not be blindly committed by this version unless the existing assignment safety evaluator marks the row safe.

Unsafe, duplicate, uncertain, incomplete, or missing matches must require manual user review.

This version must not weaken existing assignment safety rules.

### 6. Manual Correction Continuation

The flow must preserve the existing manual correction path from OCR review.

If the matching result is uncertain or unsafe, the user must still be able to manually correct the assigned team slot and other editable fields through the existing OCR correction/manual review behavior.

### 7. Return Behavior

Back/navigation from team matching or OCR review must preserve the same match context.

Preferred return target:

```text
MatchOcrReviewDestination(tournamentId, matchId)
```

or, where the team-matching state is integrated into OCR review:

```text
MatchReviewDestination(tournamentId, matchId)
```

The implementation should avoid returning to tournament list unless no safe same-match destination exists.

## Decisions

### Decision 1 — Existing matching algorithms remain authoritative

This version must use the already implemented Phase 9 matching components.

It must not create alternate normalization, similarity, scoring, ranking, threshold, or safety logic.

### Decision 2 — Matching remains review-first

Team matching results are advisory unless the existing safety evaluator marks them safe.

The user must remain able to review and correct assignments.

### Decision 3 — No automatic finalization

This version must not finalize OCR-assisted matches automatically.

Safe match finalization remains a separate explicit action and must continue to use existing finalization safeguards.

### Decision 4 — No fake OCR evidence

If OCR evidence is empty or unavailable, team matching must not invent detected players or assignments.

The correct result is an empty/manual-required/blocked review state.

### Decision 5 — Exact match identity is mandatory

The workflow must preserve the exact tournament ID and match ID through OCR review and team matching.

No matching result may be applied to another match.

### Decision 6 — Manual flow remains intact

The v0.11.1 manual flow must remain available and unaffected.

Users must still be able to manually enter placements and kills without OCR or team matching.

### Decision 7 — Screenshot flow remains intact

The v0.11.2 screenshot/OCR review navigation must remain available and unaffected.

Team matching must extend the OCR review path, not replace screenshot/OCR workflow integration.

## Acceptance Criteria

v0.11.3 is accepted when all of the following are true:

1. Team matching starts from the correct OCR review / OCR-assisted match context.
2. The exact tournament ID and match ID are preserved.
3. Existing roster data for the same tournament is used.
4. Existing OCR row evidence is used when available.
5. Existing normalization logic is used.
6. Existing player similarity matching is used.
7. Existing team candidate scoring is used.
8. Existing top-three suggestion logic is used.
9. Existing confidence threshold classification is used.
10. Existing assignment safety logic is used.
11. Matching results remain advisory unless existing safety rules mark them safe.
12. Uncertain, unsafe, duplicate, incomplete, or missing matches require manual review.
13. Existing OCR correction/manual review behavior remains available.
14. Existing OCR empty-state behavior remains unchanged.
15. v0.11.1 manual match flow still works.
16. v0.11.2 screenshot/OCR review flow still works.
17. No Room schema, Supabase, cloud sync, export, scoring, standings, OCR parser, matching algorithm, Gradle, or visual redesign changes are introduced.
18. Tests cover the connected team-matching workflow.

## Required Test Coverage

Implementation should add or update focused tests for the connected team-matching workflow.

### Unit tests

Where ViewModels or mappers expose team-matching review state, test:

* team matching preserves exact tournament ID and match ID
* detected OCR player names are matched against roster players from the same tournament
* existing top-three suggestions are surfaced
* automatic confidence tier is surfaced when existing thresholds allow it
* confirmation-required tier is surfaced when existing thresholds require it
* manual-required tier is surfaced when no safe candidate exists
* duplicate team assignment is blocked by existing safety rules
* insufficient player-match evidence is blocked by existing safety rules
* missing OCR evidence does not create fake matches
* missing or incomplete roster data results in manual-required / blocked state
* one-shot navigation or action events are consumed/cleared correctly where applicable
* manual correction state remains editable when matching is uncertain

### Compose / screen tests

Where matching results are displayed, test:

* top suggestion information is visible
* confidence/safety status is visible
* manual-required state is visible
* user can proceed to existing correction/manual review path
* no automatic finalization action is triggered by viewing suggestions
* empty OCR state remains unchanged when there is no OCR evidence

### Navigation / instrumentation tests

Add or update connected navigation coverage for:

```text
confirmed tournament details
-> draft match review
-> OCR review
-> team matching review / matching state
-> correction or same review context
-> same tournament details
```

Also cover:

* same tournament ID and match ID are preserved
* OCR review remains reachable from Match Review
* manual match flow remains accessible
* no Hilt fallback is introduced in non-Hilt navigation tests

Use existing fake/in-memory repositories, existing test ViewModel factories, and existing matching test doubles where available.

Do not introduce Hilt test infrastructure unless already required by existing tests.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

* OCR review ViewModel/state mapping
* OCR review screen display
* match review / OCR review navigation integration
* existing team-matching domain component usage
* focused unit tests
* focused screen tests
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

Targeted unit/navigation tests may be run first if the implementation primarily changes OCR review and navigation.

## Non-Regression Requirements

The implementation must preserve:

* v0.11.0 tournament setup flow
* v0.11.1 manual match flow
* v0.11.2 screenshot/OCR review flow
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
* existing OCR empty-state behavior
* existing screenshot validation behavior
* existing duplicate detection behavior
* existing local image preservation behavior
* existing cloud sync route behavior
* existing export behavior
* existing normalization behavior
* existing player similarity behavior
* existing candidate scoring behavior
* existing suggestion ranking behavior
* existing confidence threshold behavior
* existing assignment safety behavior

## Completion Definition

v0.11.3 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Team-matching workflow integration is implemented within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
