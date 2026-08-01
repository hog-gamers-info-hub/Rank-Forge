# Phase 11 v0.11.2 — Screenshot Processing Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.2 — Screenshot Processing Flow

## Purpose

Connect the already implemented screenshot intake, local image handling, OCR extraction, parsing, and OCR review foundations into one reliable screenshot-processing workflow for draft matches.

This version must make the screenshot path feel continuous from a draft match context:

```text
Tournament Details
-> Match Review / Match Screenshot Entry Point
-> Screenshot Linking / Management
-> OCR Processing
-> OCR Review
-> Manual Correction / Review Continuation
```

The purpose of this version is workflow integration only. Existing image validation, duplicate detection, local image preservation, OCR preprocessing, raw extraction, parsing, uncertainty handling, and review behavior must remain authoritative.

## Current Context

Phase 11 v0.11.0 is complete.

The tournament setup flow now carries the exact persisted tournament ID from creation into setup, enters team setup directly after creation, and returns successful roster confirmation to the same tournament details screen.

Phase 11 v0.11.1 is complete.

The manual match flow now carries the exact tournament ID and match ID through match creation, placement entry, kill entry, match review, and return-to-details navigation.

v0.11.2 starts after that. It connects the screenshot-based match-processing path for existing draft matches.

Earlier phases already introduced the individual screenshot/OCR pieces:

* Photo Picker image intake
* image validation
* screenshot linking
* duplicate screenshot detection
* local image preservation
* screenshot list/management
* ML Kit text recognition
* fixed scoreboard layout definition
* image preprocessing
* raw OCR extraction
* placement parsing
* player-name parsing
* kill parsing
* OCR uncertainty/failure handling
* real screenshot evaluation
* OCR review foundations

This version must connect those pieces cleanly without re-implementing their internal logic.

## In Scope

v0.11.2 includes only screenshot-processing workflow integration for draft matches.

In scope:

1. Start screenshot processing from the correct tournament and match context.
2. Preserve the exact tournament ID and match ID through screenshot linking, OCR processing, and OCR review.
3. Ensure linked screenshots remain associated with the same draft match.
4. Ensure screenshot replacement/unlinking remains draft-only and respects existing finalized protection.
5. Use existing image validation before preserving or linking any screenshot.
6. Use existing duplicate detection rules before accepting a screenshot.
7. Use existing local image preservation behavior for accepted screenshots.
8. Provide a clear route from screenshot-linked draft match state into OCR processing/review.
9. Ensure OCR processing uses existing preprocessing, extraction, parsing, and uncertainty handling components.
10. Route OCR results to the existing OCR review screen for the same tournament ID and match ID.
11. Preserve existing manual match behavior from v0.11.1.
12. Add or update focused tests for the connected screenshot-processing flow.

## Out of Scope

This version must not implement or modify:

* team matching flow
* automatic team assignment
* confidence threshold decisions
* safe automatic finalization
* roster OCR workflow
* roster screenshot intake/crop/set association behavior
* match finalization workflow as a new integration step
* standings recalculation workflow
* cloud synchronization workflow
* export workflow
* Supabase schema, RLS, RPCs, edge functions, or cloud upload behavior
* Room schema, migrations, entities, DAOs, or database version
* scoring engine logic
* tie-break logic
* protected correction logic
* tournament creation/setup flow
* manual match flow behavior except where screenshot entry points must coexist with it
* roster confirmation behavior
* authentication behavior
* visual redesign

## Canonical Flow

### 1. Entry Point

Screenshot processing starts from an existing draft match context.

The expected starting context is one of the existing match-related routes, normally:

```text
MatchReviewDestination(tournamentId, matchId)
```

or an existing screenshot-management entry point for the same match.

The tournament ID and match ID must be preserved exactly.

The flow must not infer the match from list position, visible ordering, match number alone, or screenshot metadata alone.

### 2. Screenshot Linking

When the user selects or links a screenshot, the workflow must use existing image intake and validation behavior.

Accepted screenshot linking must satisfy existing rules:

* valid supported image MIME type
* valid image dimensions
* valid image size/pixel bounds
* duplicate detection rules pass
* draft-match protection rules pass
* local image preservation succeeds

The screenshot must remain associated with the exact same tournament ID and match ID.

If validation fails, duplicate detection fails, preservation fails, or the match is not editable, the workflow must remain on the current screen and surface the existing error state.

### 3. Screenshot Management

The user must be able to view/manage linked screenshots for the same match using existing screenshot list/management behavior.

Replacement and unlinking must preserve existing finalized protection rules.

This version must not weaken any finalized-match protection.

### 4. OCR Processing

OCR processing must start only from a screenshot associated with the same draft match.

The OCR processing flow must use existing OCR components:

* fixed scoreboard layout definition
* preprocessing
* raw text extraction
* placement parsing
* player-name parsing
* kill parsing
* uncertainty/failure handling

This version must not duplicate OCR extraction, parsing, or validation logic in navigation code.

### 5. OCR Review

After OCR processing completes, the workflow should route to the OCR review screen for the same IDs:

```text
MatchOcrReviewDestination(tournamentId, matchId)
```

The OCR review screen must load the OCR result state for the exact same match.

If OCR processing fails or produces uncertain fields, the existing OCR failure/uncertainty handling must remain authoritative.

### 6. Returning From OCR Review

From OCR review, back/navigation behavior should return to the relevant same-match context.

Preferred return target:

```text
MatchReviewDestination(tournamentId, matchId)
```

Fallback behavior is allowed if the review destination is not already present:

```text
navigate(MatchReviewDestination(tournamentId, matchId))
```

The fallback must avoid creating a confusing stack when possible.

## Navigation Decisions

### Decision 1 — Screenshot workflow must preserve tournament ID and match ID

Every screenshot-processing step must carry the exact tournament ID and match ID.

The implementation must not rely on match number, tournament list position, screenshot filename, screenshot creation time, or parsed OCR values to identify the target match.

### Decision 2 — Existing screenshot validation remains authoritative

Image validation, duplicate detection, local image preservation, link/replacement/unlink behavior, and finalized protection rules already exist.

This version must connect those behaviors into a workflow.

It must not duplicate or redefine the underlying screenshot validation rules.

### Decision 3 — Existing OCR pipeline remains authoritative

The existing OCR preprocessing, extraction, parsing, and uncertainty/failure handling must remain the source of truth.

This version must not create a second OCR pipeline, alternate parser, or new confidence model.

### Decision 4 — Screenshot processing does not auto-finalize

OCR results must go to review/correction.

This version must not automatically finalize match results.

Finalization and standings integration belong to later Phase 11 versions.

### Decision 5 — Screenshot processing does not perform team matching

Screenshot OCR may produce detected text, placements, player names, and kills.

This version must not resolve teams automatically using roster matching.

Team matching belongs to v0.11.3.

### Decision 6 — Manual flow must remain available

The v0.11.1 manual match flow must remain intact.

Users must still be able to manually enter or correct placements and kills without using screenshot/OCR processing.

Screenshot processing is an additional workflow path, not a replacement for manual entry.

## Acceptance Criteria

v0.11.2 is accepted when all of the following are true:

1. Screenshot processing starts from the correct tournament ID and match ID context.
2. Linked screenshots remain associated with the same draft match.
3. Existing image validation is used before accepting a screenshot.
4. Existing duplicate detection rules are preserved.
5. Existing local image preservation behavior is preserved.
6. Replacement/unlinking behavior remains draft-only and does not weaken finalized protection.
7. OCR processing uses existing preprocessing, extraction, parsing, and uncertainty handling.
8. Successful OCR processing routes to OCR review for the same tournament ID and match ID.
9. OCR failure/uncertainty states remain visible and unchanged.
10. OCR review back/navigation returns to the same match context.
11. Manual match flow from v0.11.1 still works.
12. No team matching, automatic assignment, finalization, standings, cloud sync, export, schema, or Supabase behavior is introduced.
13. Tests cover the connected screenshot-processing workflow.

## Required Test Coverage

Implementation should add or update focused tests for the connected screenshot workflow.

### Unit tests

Where ViewModels expose screenshot/OCR navigation or processing events, test:

* screenshot linking uses the exact tournament ID and match ID
* accepted screenshot state remains associated with the same match
* invalid image candidates do not proceed to OCR/review navigation
* duplicate screenshots do not proceed to OCR/review navigation
* finalized or non-editable match state blocks screenshot mutation
* successful OCR processing emits navigation or review state for the same tournament ID and match ID
* OCR failure emits existing failure state and no invalid forward navigation
* one-shot navigation events are consumed/cleared correctly

### Navigation/instrumentation tests

Add or update connected navigation coverage for the screenshot-processing path:

```text
confirmed tournament details
-> draft match review
-> screenshot linking / screenshot management
-> OCR processing
-> OCR review
-> same match review / details context
```

Also cover:

* screenshot workflow preserves the same tournament ID and match ID
* OCR review opens for the same match
* OCR review back returns to the expected same-match context
* manual match flow still remains accessible

Tests should use existing fake/in-memory repositories, existing test ViewModel factories, and existing local image/OCR test doubles if available.

Do not introduce Hilt test infrastructure unless already required by existing tests.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

* match review route/screen entry point for screenshot processing
* screenshot linking/management route integration
* OCR processing trigger or ViewModel integration
* OCR review route integration
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
* v0.11.1 manual match flow
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
* existing screenshot validation behavior
* existing duplicate detection behavior
* existing local image preservation behavior

## Completion Definition

v0.11.2 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Screenshot-processing workflow integration is implemented within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
