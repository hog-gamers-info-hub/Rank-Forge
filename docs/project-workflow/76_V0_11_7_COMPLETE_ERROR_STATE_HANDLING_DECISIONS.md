# Phase 11 v0.11.7 — Complete Error-State Handling Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.7 — Complete Error-State Handling

## Purpose

Add consistent error states, retry paths, warning states, and recovery actions across the completed Rank Forge app workflow.

This version integrates already implemented workflow screens and error primitives into one consistent app-level error-handling experience.

The canonical outcome is:

```text
User action
-> validation / local persistence / OCR / sync / finalization / export operation
-> success, warning, retryable error, blocked error, or recovery state
-> clear user action
-> same tournament/match context preserved
```

This version is workflow integration only.

It must not redesign domain behavior, persistence schema, Supabase behavior, OCR algorithms, matching algorithms, scoring, standings, finalization, export contracts, or cloud-sync internals.

## Current Context

Phase 11 v0.11.0 is complete.

Tournament creation enters setup directly, preserves the exact persisted tournament ID, and returns successful roster confirmation to the same tournament details screen.

Phase 11 v0.11.1 is complete.

Manual match creation routes through placement entry, kill entry, match review, and return-to-details while preserving the exact tournament ID and match ID.

Phase 11 v0.11.2 is complete.

Match Review routes safely to OCR Review for linked draft screenshots, preserves exact tournament ID and match ID, and returns OCR Review back to the same Match Review context.

Phase 11 v0.11.3 is complete.

OCR Review safely loads existing precomputed OCR/team-matching evidence when available, preserves exact tournament ID and match ID, keeps empty OCR state unchanged, avoids fake matching results, and preserves manual review behavior.

Phase 11 v0.11.4 is complete.

Existing finalization-to-standings wiring was verified. Valid finalization remains validation-gated, same-tournament standings/details context remains available after finalization, and finalized protection remains intact.

Phase 11 v0.11.5 is complete.

Existing cloud-sync workflow wiring was verified inside the approved boundary. Local-first workflow remains intact, queued cloud-operation state preserves tournament/match identity, and finalized read-only protection remains intact.

Phase 11 v0.11.6 is complete.

Android-facing finalized-only CSV export preparation was added. Export preserves exact tournament/match identity, blocks draft/incomplete states, validates UTF-8 output, and exposes Google Sheets as unavailable/deferred because no Android client exists.

v0.11.7 starts after those workflow integrations.

## In Scope

v0.11.7 includes only complete workflow error-state integration.

In scope:

1. Standardize visible error/warning/retry/recovery state across active workflow screens.
2. Preserve exact tournament ID and match ID after errors.
3. Keep users in the same safe context when operations fail.
4. Surface validation errors consistently.
5. Surface local persistence errors consistently where available.
6. Surface screenshot validation errors consistently.
7. Surface OCR empty/failure/manual-required states consistently.
8. Surface team-matching uncertain/manual-required/unsafe states consistently.
9. Surface finalization blocked/failure states consistently.
10. Surface standings unavailable/retry states consistently.
11. Surface cloud-sync pending/retryable/conflict/offline/authentication-required states consistently where already available.
12. Surface export blocked/unavailable/failure/deferred states consistently.
13. Add retry actions only where existing retry-safe behavior already exists.
14. Add recovery actions only where existing recovery-safe behavior already exists.
15. Add warnings for destructive, blocked, finalized, unavailable, incomplete, or deferred operations where current workflow supports them.
16. Add focused unit, screen, and navigation tests for error-state behavior.

## Out of Scope

This version must not implement or modify:

- domain validation algorithms
- scoring logic
- standings logic
- tie-break logic
- finalization logic
- protected correction domain behavior
- OCR extraction
- OCR parsing
- OCR preprocessing
- team matching algorithms
- confidence thresholds
- assignment safety rules
- screenshot validation rules
- duplicate detection rules
- local image preservation behavior
- Room schema, entities, DAOs, migrations, or database version
- Supabase schema, migrations, RLS, RPCs, or Edge Functions
- authentication provider behavior
- session token storage behavior
- cloud-sync queue identity
- cloud-sync retry/idempotency/conflict algorithms
- export schemas
- Google Sheets request contracts
- CSV serializers/exporters
- Android file/share infrastructure
- Google Sheets Android client
- production deployment
- visual redesign
- new analytics
- new notifications

## Canonical Error-State Categories

v0.11.7 should use existing error/result/status types where available.

The app should avoid one-off screen-specific behavior when the same category already exists elsewhere.

### 1. Validation Errors

Examples:

- missing required tournament fields
- incomplete roster
- invalid team slot
- missing placement row
- duplicate placement
- missing team assignment
- duplicate team assignment
- missing kills
- negative kills
- invalid correction draft
- invalid finalization input

Behavior:

- stay on the same screen
- show the existing validation message/state
- preserve entered draft data
- do not navigate away
- do not mutate finalized data
- allow correction where the existing workflow supports it

### 2. Local Persistence Errors

Examples:

- local save failure
- local load failure
- missing local record
- deleted or unavailable match/tournament
- restart recovery failure

Behavior:

- preserve current route identity where possible
- show safe error state
- provide retry/reload only if existing load behavior supports retry
- provide safe return-to-details/list action only when exact same record cannot be loaded
- do not create replacement records automatically

### 3. Authentication Errors

Examples:

- unauthenticated cloud/export action
- expired/restoring session
- logout/session failure
- cloud operation requires auth

Behavior:

- keep local workflow usable where existing local-first rules allow it
- block authenticated-only action
- show authentication-required state where already supported
- do not erase local data
- do not redirect unexpectedly from active tournament/match flow unless existing auth guard requires it

### 4. Cloud Sync Errors

Examples:

- pending sync
- offline
- retryable sync failure
- conflict
- duplicate suppression
- restoration unavailable
- permission/ownership failure

Behavior:

- preserve local-first workflow
- do not silently overwrite conflict state
- show retry only where existing retry is safe
- do not bypass RLS/ownership rules
- do not weaken finalized protection
- do not duplicate queued operations through screen reload

### 5. Screenshot/OCR Errors

Examples:

- unsupported image type
- image too large
- image too small
- duplicate screenshot
- screenshot missing
- OCR unavailable
- OCR empty result
- OCR low confidence
- OCR parsing incomplete
- manual review required

Behavior:

- keep the same match context
- preserve screenshot state where existing behavior supports it
- do not create fake OCR evidence
- do not auto-finalize
- keep manual correction path available

### 6. Team-Matching Warnings

Examples:

- confirmation required
- manual required
- duplicate team assignment
- insufficient player matches
- insufficient candidate lead
- missing roster
- missing OCR evidence
- unsafe automatic assignment

Behavior:

- show advisory state
- keep manual correction available
- do not commit unsafe assignment automatically
- do not use advisory suggestions for standings/export before confirmation/finalization

### 7. Finalization Errors

Examples:

- validation failed
- draft incomplete
- unsafe OCR/correction state
- already finalized
- finalized protection violation
- correction required before finalization

Behavior:

- remain in same match review context
- do not partially finalize
- do not update standings from invalid data
- preserve finalized protection
- keep protected correction entry where supported

### 8. Export Errors and Warnings

Examples:

- export unavailable
- match not finalized
- standings unavailable
- CSV export blocked
- Google Sheets unavailable/deferred
- authentication required
- outcome uncertain
- retryable failure
- export already in progress
- duplicate export suppressed

Behavior:

- stay in the same tournament/match context
- do not export draft data
- do not call Google/Supabase if no safe client exists
- do not expose secrets or raw upstream errors
- do not retry uncertain exports blindly
- preserve finalized-data protection

## Decisions

### Decision 1 — Existing domain behavior remains authoritative

v0.11.7 must surface existing errors and states.

It must not redefine validation, matching, scoring, finalization, sync, or export rules.

### Decision 2 — Same-context recovery is mandatory

After an error, warning, blocked operation, or retryable failure, the app must preserve the safest same tournament/match context whenever possible.

### Decision 3 — Retry actions must be safe

Retry actions may be added only when the underlying operation is already retry-safe.

No blind duplicate save, sync, finalization, or export retry may be introduced.

### Decision 4 — Warnings must not mutate data

Warnings and advisory states must not commit data, finalize matches, update standings, or export data automatically.

### Decision 5 — Local-first behavior remains intact

Cloud/auth errors must not block local tournament/match workflow unless existing rules already require blocking.

### Decision 6 — Finalized protection remains intact

Error recovery must not reopen finalized data through ordinary draft paths.

Protected correction behavior remains authoritative.

### Decision 7 — No secrets or raw upstream errors

Error UI must not expose stack traces, Supabase JWTs, Google credentials, spreadsheet IDs, worksheet metadata, private keys, access tokens, service-account emails, or raw upstream responses.

### Decision 8 — No new infrastructure

This version may add small UI-state fields, ViewModel mappings, retry callbacks, and tests.

It must not add new persistence, sync, export, or network infrastructure.

## Acceptance Criteria

v0.11.7 is accepted when all of the following are true:

1. Common validation failures keep the user in the same context.
2. Validation errors preserve entered draft data where existing workflow supports it.
3. Missing/unavailable tournament and match states are safely surfaced.
4. Screenshot validation errors remain visible and recoverable.
5. OCR empty/failure states remain visible and manual-review safe.
6. Team-matching manual-required/unsafe states remain advisory and recoverable.
7. Finalization failures do not update standings.
8. Finalization failures preserve exact tournament ID and match ID.
9. Finalized protection remains intact after error recovery.
10. Cloud-sync pending/retryable/conflict/offline states remain local-first where already supported.
11. Export blocked/unavailable/deferred states are visible and safe.
12. Retry actions exist only for retry-safe existing operations.
13. Same tournament/match context is preserved after error, warning, blocked, and retryable states.
14. No fake OCR evidence, fake sync success, fake export success, or fake Google Sheets action is introduced.
15. No raw secrets, stack traces, tokens, spreadsheet metadata, or upstream payloads are displayed.
16. v0.11.0 tournament setup flow still works.
17. v0.11.1 manual match flow still works.
18. v0.11.2 screenshot/OCR review flow still works.
19. v0.11.3 team-matching advisory state remains safe.
20. v0.11.4 finalization and standings flow still works.
21. v0.11.5 cloud synchronization flow remains intact.
22. v0.11.6 export flow remains intact.
23. No Room schema, Supabase, auth, cloud-sync algorithm, export schema, OCR algorithm, matching algorithm, scoring, standings, Gradle, schema, generated-file, or visual redesign changes are introduced unless a compile-blocking issue is discovered inside the approved boundary.

## Required Test Coverage

Implementation should add or update focused tests for complete error-state handling.

### Unit tests

Where ViewModels expose workflow state, test:

- validation failure preserves exact tournament ID and match ID
- validation failure does not navigate away
- missing tournament state is safe
- missing match state is safe
- screenshot duplicate/invalid state remains recoverable
- OCR empty state remains recoverable
- team-matching manual-required state remains advisory
- finalization failure does not update standings
- finalized protection remains intact after error state
- cloud pending/retryable/conflict state preserves local workflow where currently exposed
- export blocked/deferred state preserves same context
- retry action is available only when retry-safe
- retry action does not duplicate finalization/export/sync
- dismissed one-shot error messages do not repeat incorrectly
- reload/retry uses exact tournament ID and match ID

### Screen tests

Where visible UI exists, test:

- validation error is visible
- warning state is visible
- blocked action state is visible
- retry action is visible only when appropriate
- recovery action is visible only when appropriate
- finalized protection warning is visible where supported
- export unavailable/deferred status is visible
- no secret/raw upstream metadata is displayed
- user can continue local workflow while cloud sync is pending where supported

### Navigation / instrumentation tests

Add or update connected navigation coverage only where appropriate.

Preferred coverage:

```text
tournament details
-> draft/manual match workflow
-> validation/error state
-> recovery/retry/correction action
-> same tournament/match context
```

Also preserve existing connected coverage for:

- v0.11.0 setup
- v0.11.1 manual match flow
- v0.11.2 OCR review navigation
- v0.11.4 finalization to standings context
- v0.11.6 export status context

Do not add Hilt infrastructure unless already required.

Use existing fake/in-memory repositories and existing navigation-test wiring.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

- shared or existing UI-state error/status fields
- Tournament Setup ViewModel/state/screen
- Tournament Details ViewModel/state/screen
- Match Creation / Placement / Kill Entry ViewModel/state/screen
- Match Review ViewModel/state/screen
- OCR Review ViewModel/state/screen
- Export status state already added in v0.11.6
- navigation tests
- focused unit/screen tests

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

No Supabase, Google, production, Docker, or deployment verification is authorized for this version.

## Non-Regression Requirements

The implementation must preserve:

- tournament setup flow
- manual match flow
- screenshot/OCR review flow
- team-matching advisory flow
- finalization and standings flow
- cloud-sync workflow
- export flow
- finalized-data protection
- protected correction workflow
- Room local persistence
- authentication/session restoration
- existing sync/conflict behavior
- existing CSV export behavior
- existing Google Sheets deferred/unavailable state
- scoring
- standings
- tie-break behavior
- existing navigation destinations
- existing test architecture

## Completion Definition

v0.11.7 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Consistent error, warning, retry, and recovery handling is implemented or verified within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
