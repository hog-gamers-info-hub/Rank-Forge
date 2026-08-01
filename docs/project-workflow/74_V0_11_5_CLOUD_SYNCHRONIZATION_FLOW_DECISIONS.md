# Phase 11 v0.11.5 — Cloud Synchronization Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.5 — Cloud Synchronization Flow

## Purpose

Connect local persistence, offline queue, authenticated Supabase synchronization, conflict handling, retry behavior, and recovery into one reliable workflow.

This version integrates already implemented local Room persistence and Phase 6 cloud-sync primitives into the completed Phase 11 app workflow.

The canonical outcome is:

```text id="9wwgu4"
Authenticated user
-> create/update tournament, roster, draft match, finalized match, or correction locally
-> persist locally first
-> enqueue sync work when needed
-> upload/retry through existing cloud sync mechanism
-> recover after app restart/network loss
-> preserve local workflow state and finalized-data protection
```

This version is workflow integration only.

It must not redesign Supabase schema, RLS, sync queue identity, conflict resolution rules, authentication, Room schema, finalization rules, scoring, standings, OCR, export, or protected correction behavior.

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

Existing production finalization-to-standings wiring was verified through tests. Valid finalization remains validation-gated, standings refresh remains available for the same tournament context, and finalized protection remains intact.

v0.11.5 starts after those workflow integrations.

Earlier versions already implemented the primitives this version must connect:

- Room local persistence
- app restart recovery
- offline workflow
- Supabase authentication
- session restoration
- backend schema
- RLS and ownership
- tournament and roster cloud upload/restoration
- draft match sync
- finalized match sync
- offline sync queue
- idempotency and duplicate prevention
- conflict resolution
- finalized data protection
- protected corrections and audit history

This version must connect existing pieces into the active app workflow. It must not re-implement them.

## In Scope

v0.11.5 includes only cloud synchronization workflow integration.

In scope:

1. Ensure authenticated workflow uses existing local-first persistence.
2. Ensure tournament creation/update syncs through the existing queue/cloud mechanism.
3. Ensure roster setup/update syncs through the existing queue/cloud mechanism.
4. Ensure draft match creation/update syncs through the existing queue/cloud mechanism.
5. Ensure finalized match sync uses existing finalized protection rules.
6. Ensure protected correction sync uses existing correction/audit behavior where already available.
7. Ensure offline-created or offline-updated records are recoverable after app restart.
8. Ensure queued work remains retryable after transient failures.
9. Ensure same-owner cloud restoration uses existing ownership/RLS-safe behavior.
10. Ensure conflict state is surfaced or preserved through the existing conflict workflow.
11. Ensure local workflow remains usable while sync is pending.
12. Ensure sync does not block local tournament/match workflow unless existing rules require it.
13. Ensure finalization, standings, OCR review, and team-matching states remain local-authoritative until confirmed by existing sync behavior.
14. Add or update focused tests for workflow-level sync integration.

## Out of Scope

This version must not implement or modify:

- Supabase schema
- Supabase migrations
- RLS policies
- RPC signatures
- Edge functions
- service-role behavior
- authentication provider behavior
- session token storage behavior
- Room schema, entities, DAOs, migrations, or database version
- sync queue database schema
- sync queue identity rules
- idempotency fingerprint rules
- conflict resolution algorithms
- finalized-data protection rules
- protected correction domain rules
- scoring logic
- standings logic
- tie-break logic
- OCR extraction
- OCR parsing
- OCR preprocessing
- screenshot linking
- screenshot validation
- duplicate screenshot detection
- local image preservation
- team matching algorithms
- export workflow
- CSV save/share workflow
- Google Sheets workflow
- production deployment
- production Supabase access
- visual redesign
- new analytics or notifications

## Canonical Flow

### 1. Authenticated Local-First Workflow

When the user is authenticated, app workflow remains local-first.

Actions such as:

- tournament creation
- tournament update
- roster confirmation
- draft match creation
- placement entry
- kill entry
- match review updates
- match finalization
- protected correction

must continue to persist locally first using existing behavior.

Cloud sync must be additive to local persistence, not a replacement for local persistence.

### 2. Queueing

When a local change requires cloud synchronization, the workflow must use the existing offline sync queue mechanism.

The implementation must not create a second queue.

The implementation must not create a parallel retry system.

The implementation must not create new operation identity rules.

Queued operations must preserve authoritative identifiers:

```text id="6ei277"
ownerId
tournamentId
matchId where applicable
roster/team slot identifiers where applicable
correction/audit identifiers where applicable
operation type
local revision/version where already supported
```

### 3. Retry and Recovery

Queued sync work must remain recoverable after:

- app restart
- temporary network failure
- temporary Supabase failure
- session restoration delay
- switching away from and back to the relevant screen

Retry behavior must use existing sync queue behavior.

The implementation must not perform blind duplicate uploads.

The implementation must not ignore existing idempotency/duplicate-prevention rules.

### 4. Restoration

When cloud restoration is available, it must restore only data owned by the authenticated user under existing RLS/ownership rules.

Restoration must preserve:

- tournament identity
- roster identity
- draft match identity
- finalized match identity
- correction/audit identity
- local finalized protection
- standings derived from finalized local/application data

The implementation must not import cross-owner data.

The implementation must not trust unauthenticated remote data.

### 5. Conflict Handling

If existing conflict resolution detects a conflict, v0.11.5 must preserve or surface the existing conflict state.

The implementation must not silently overwrite remote or local data.

The implementation must not weaken draft conflict behavior.

The implementation must not bypass protected finalized correction rules.

### 6. Sync Status and User Flow

Where existing UI/state already exposes sync status, the workflow should keep it accurate across the active Phase 11 flows.

Acceptable statuses include existing equivalents of:

- synced
- pending sync
- syncing
- failed/retryable
- conflict
- offline
- authentication required

This version should not introduce a visual redesign.

If sync status is not currently exposed in a given screen, implementation may add minimal existing-style state/test-tag support only inside approved boundaries.

### 7. Finalized Match Safety

Finalized matches must remain protected during sync.

Cloud sync must not reopen, mutate, or duplicate finalized match data through ordinary draft paths.

Protected corrections must continue to use existing protected correction/audit behavior.

### 8. Same-Context Navigation

Sync state changes must not break the completed Phase 11 navigation flows:

```text id="s0q7j3"
Tournament creation -> setup -> tournament details
Manual match creation -> placement -> kills -> match review -> tournament details
Match review -> OCR review -> same match review
Match finalization -> same tournament details / standings context
```

## Decisions

### Decision 1 — Local-first behavior remains authoritative

The app remains usable from local Room state.

Cloud sync must not replace local persistence.

### Decision 2 — Existing sync queue remains authoritative

v0.11.5 must use the already implemented offline sync queue and duplicate-prevention behavior.

No second queue, second retry loop, or second operation identity system may be introduced.

### Decision 3 — Existing Supabase security remains unchanged

No schema, RLS, RPC, edge function, auth, or service-role change is authorized in this version.

### Decision 4 — Sync must preserve exact identity

Sync workflow must preserve exact owner, tournament, roster, match, finalization, and correction identities.

No local record may be uploaded into the wrong tournament, match, or user account.

### Decision 5 — Conflict handling remains explicit

Existing conflict states must be preserved or surfaced.

The workflow must not silently overwrite conflicting local or remote state.

### Decision 6 — Finalized protection remains intact

Cloud sync must not weaken finalized match protection or protected correction audit behavior.

### Decision 7 — Offline and restart recovery are required

Pending sync work must survive app restart and retry through existing recovery behavior.

### Decision 8 — Export remains later

CSV and Google Sheets workflow integration remains v0.11.6.

v0.11.5 must not add export UI or export calls.

## Acceptance Criteria

v0.11.5 is accepted when all of the following are true:

1. Authenticated local-first workflow remains intact.
2. Tournament creation/update can use existing cloud sync workflow.
3. Roster setup/update can use existing cloud sync workflow.
4. Draft match creation/update can use existing cloud sync workflow.
5. Finalized match sync preserves finalized-data protection.
6. Protected correction sync preserves existing audit/correction behavior where available.
7. Pending sync work survives app restart through existing queue behavior.
8. Retryable failures remain retryable without duplicate uploads.
9. Restoration uses existing owner/RLS-safe behavior.
10. Conflict state is preserved or surfaced instead of silently overwritten.
11. Offline local workflow remains usable while sync is pending.
12. Sync status is accurate where existing UI/state supports it.
13. Exact tournament ID and match ID are preserved through sync-triggering workflows.
14. v0.11.0 tournament setup flow still works.
15. v0.11.1 manual match flow still works.
16. v0.11.2 screenshot/OCR review flow still works.
17. v0.11.3 team-matching advisory state remains safe.
18. v0.11.4 finalization and standings flow still works.
19. No Supabase schema, RLS, RPC, Edge Function, auth, Room schema, sync queue identity, conflict algorithm, scoring, standings, OCR, screenshot, team matching, export, Gradle, or visual redesign changes are introduced unless implementation proves a compile-blocking issue inside the approved boundary.

## Required Test Coverage

Implementation should add or update focused tests for cloud-sync workflow integration.

### Unit tests

Where ViewModels, repositories, or sync coordinators expose workflow state, test:

- tournament creation/update enqueues or triggers existing sync behavior
- roster confirmation/update enqueues or triggers existing sync behavior
- draft match creation/update enqueues or triggers existing sync behavior
- finalized match sync uses existing finalized-safe path
- protected correction sync preserves existing correction/audit behavior where available
- offline/pending state is preserved in UI state where currently exposed
- retryable failure state remains retryable
- conflict state is preserved/surfaced
- exact tournament ID and match ID are preserved
- sync-triggering workflow does not mutate finalized data incorrectly
- local workflow remains available while sync is pending
- restoration loads same-owner cloud data only through existing behavior
- no duplicate upload is triggered by reloading the same screen

### Screen tests

Where sync status is displayed, test:

- pending sync status is visible
- syncing status is visible
- retryable failure/offline status is visible
- conflict state is visible or reachable
- user can continue local workflow while sync is pending
- finalized status remains protected while sync is pending
- no export action appears as part of v0.11.5

### Navigation / instrumentation tests

Add or update connected navigation coverage only where appropriate.

Preferred connected coverage:

```text id="85g2i7"
authenticated / restored session context
-> tournament details
-> create or update local data
-> observe sync-safe state
-> continue to same tournament context
```

Also preserve existing connected navigation coverage for:

- v0.11.0 setup
- v0.11.1 manual match flow
- v0.11.2 OCR review navigation
- v0.11.4 finalization to standings context

Do not add Hilt infrastructure unless already required.

Use existing fake/in-memory repositories and existing navigation-test wiring where possible.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

- existing sync status/state mapping
- existing tournament details workflow state
- existing tournament setup/roster workflow state
- existing match review workflow state
- existing sync queue trigger/retry surface where already available
- focused ViewModel tests
- focused screen tests
- focused navigation tests

The implementation prompt must list the exact approved file boundary before edits.

If implementation discovers that a required file is outside the approved boundary, it must stop and report the needed file instead of editing broadly.

## Verification Policy

Codex should run only lightweight verification unless explicitly instructed otherwise.

Codex-side check:

```text id="cy8ywx"
git diff --check
```

Local verification outside Codex should include:

```text id="0mqnct"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

Supabase local verification is required only if Supabase files are unexpectedly changed, which this version does not authorize.

## Non-Regression Requirements

The implementation must preserve:

- tournament management
- roster management
- manual match processing
- screenshot/OCR review navigation
- team-matching advisory behavior
- scoring
- standings
- Room local persistence
- authentication/session restoration
- existing cloud sync primitives
- existing offline sync queue behavior
- existing conflict resolution behavior
- finalized-data protection
- protected correction workflow
- export primitives
- CSV/Google Sheets server-side contracts
- existing navigation destinations
- existing test architecture

## Completion Definition

v0.11.5 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Cloud synchronization workflow integration is implemented or verified within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
