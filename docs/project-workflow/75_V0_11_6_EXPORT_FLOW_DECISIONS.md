# Phase 11 v0.11.6 — Export Flow Decisions

## Status

Approved for implementation.

## Version

Phase 11 — Workflow Integration
v0.11.6 — Export Flow

## Purpose

Connect finalized Rank Forge tournament results to the already implemented CSV and Google Sheets export primitives.

This version integrates Android-facing export workflow into the completed local, cloud, finalization, standings, and Phase 10 export foundations.

The canonical outcome is:

```text id="x36fl2"
Finalized match / finalized tournament standings
-> export action
-> canonical finalized application data
-> CSV export and/or Google Sheets export
-> safe success, retry, verification, or blocked/error state
-> same tournament context preserved
```

This version is workflow integration only.

It must not redefine CSV schemas, Google Sheets schemas, export request contracts, scoring rules, standings rules, tie-break behavior, finalized-data validation, Supabase schema, Edge Function behavior, Google credentials, export idempotency rules, or post-export verification behavior.

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

Phase 11 v0.11.5 is complete.

Existing cloud-sync workflow wiring was verified through tests. Local-first workflow remains intact, queued cloud-operation state preserves tournament/match identity, and finalized read-only protection remains intact.

v0.11.6 starts after those workflow integrations.

Earlier Phase 10 versions already implemented the export primitives this version must connect:

* CSV match export
* CSV standings export
* canonical CSV serialization and escaping
* Google Sheets Edge Function foundation
* Google Sheets connection verification
* Google Sheets finalized match export
* Google Sheets tournament standings export
* export retry and idempotency
* export-operation state tracking
* duplicate export suppression
* uncertain-outcome handling
* post-export verification and uncertain-write reconciliation
* approved 20-column match export schema
* approved 20-column standings export schema

This version must connect existing pieces into the Android app workflow. It must not re-implement them.

## In Scope

v0.11.6 includes only Android-facing export workflow integration.

In scope:

1. Surface export actions from the correct finalized match and/or tournament standings context.
2. Preserve exact tournament ID and match ID during export actions.
3. Allow finalized match results to be exported to existing CSV format.
4. Allow finalized tournament standings to be exported to existing CSV format.
5. Allow finalized match results to be sent to the existing Google Sheets export operation where current Android networking/auth infrastructure supports it.
6. Allow finalized tournament standings to be sent to the existing Google Sheets export operation where current Android networking/auth infrastructure supports it.
7. Allow Google Sheets connection verification through the existing authenticated Edge Function operation where current Android networking/auth infrastructure supports it.
8. Use only finalized application data as export source.
9. Block or hide export actions for draft, incomplete, invalid, or unfinalized match data.
10. Preserve existing scoring, standings, tie-break, correction, and finalized-data protection behavior.
11. Surface safe export status where currently supported: ready, unavailable, exporting, succeeded, retryable failure, in progress, outcome uncertain, verification failed, authentication required, or blocked.
12. Preserve same tournament context after export success/failure.
13. Add or update focused tests for export workflow integration.

## Out of Scope

This version must not implement or modify:

* CSV match schema
* CSV standings schema
* CSV column order
* CSV escaping rules
* Google Sheets match schema
* Google Sheets standings schema
* Google worksheet names
* Google worksheet headers
* Google append ranges
* Google write mode
* Google Drive API access
* Google worksheet creation
* Google worksheet formatting
* Google OAuth from Android
* Android storage of Google credentials
* Android direct Google Sheets API calls
* Supabase schema
* Supabase migrations
* RLS policies
* RPC signatures
* Edge Function request contracts
* Edge Function response contracts
* export idempotency algorithm
* export-operation fingerprinting
* retry lease rules
* uncertain-outcome reconciliation rules
* post-export verification algorithm
* production deployment
* production Supabase secrets
* production Google secrets
* Room schema, entities, DAOs, migrations, or database version
* scoring logic
* standings logic
* tie-break logic
* finalization logic
* protected correction domain behavior
* OCR extraction
* OCR parsing
* screenshot handling
* team matching algorithms
* cloud sync redesign
* authentication provider behavior
* visual redesign
* new analytics or notifications

## Canonical Flow

### 1. Export Entry Points

Export actions may be surfaced only from finalized/result-safe contexts.

Accepted contexts:

```text id="kuvgb3"
TournamentDetailsDestination(tournamentId)
MatchReviewDestination(tournamentId, matchId)
```

The implementation must preserve:

```text id="6kbc65"
tournamentId
matchId where exporting one finalized match
```

The implementation must not infer export identity from:

* visible UI order
* match number alone
* stale selected state
* previous route memory
* screenshot filename
* OCR text
* advisory team-matching suggestions
* Google Sheets row position
* CSV filename alone

### 2. Export Source of Truth

The export source must be finalized application data only.

Allowed export sources:

* finalized match rows from existing application/domain/repository state
* finalized tournament standings from existing standings state
* protected correction outputs only after they are accepted into finalized application state
* existing canonical export mappers/serializers where already implemented

Prohibited export sources:

* draft match rows
* incomplete placement or kill rows
* raw OCR evidence
* unconfirmed OCR review rows
* advisory team-matching suggestions
* stale cached screen-only values
* Google Sheets read-back values
* manually edited spreadsheet rows

### 3. CSV Export

CSV export must use existing CSV primitives.

CSV match export must preserve the existing approved match export schema.

CSV standings export must preserve the existing approved standings export schema.

The Android workflow may expose save/share/open behavior only if existing app capability already supports it or can be connected inside the approved boundary.

The implementation must not alter:

* headers
* column count
* column order
* string escaping
* newline handling
* numeric formatting
* row ordering
* finalized-data eligibility
* schema version values

If current Android code already has CSV generation but lacks UI workflow, connect it narrowly.

If current Android code lacks a safe file save/share mechanism inside the approved boundary, do not invent storage architecture. Report the missing boundary.

### 4. Google Sheets Export

Google Sheets export must use the existing authenticated Supabase Edge Function.

Canonical function:

```text id="j9p7eq"
google-sheets-export
```

Allowed operations are existing operations only:

```text id="3mvw4i"
verify_connection
export_match
export_standings
```

The Android app must not call Google OAuth directly.

The Android app must not call the Google Sheets API directly.

The Android app must not receive, store, log, or display Google access tokens, Google private keys, service-account emails, spreadsheet IDs, or service-account configuration.

The Android app may call only the authenticated Supabase Edge Function through existing Supabase/network infrastructure.

### 5. Frozen Google Sheets Request Contracts

The Android workflow must preserve the existing public request contracts.

For match export:

```json
{
  "operation": "export_match",
  "tournament_id": "<tournament_id>",
  "match_id": "<match_id>",
  "rows": []
}
```

For standings export:

```json
{
  "operation": "export_standings",
  "tournament_id": "<tournament_id>",
  "rows": []
}
```

For connection verification:

```json
{
  "operation": "verify_connection"
}
```

The Android client must not send new top-level fields.

The Android client must not send:

* `idempotency_key`
* `retry_token`
* `operation_id`
* `attempt`
* `force`
* `force_retry`
* `force_export`
* `verify`
* `verification_id`

Retry safety and idempotency remain server-side behavior.

### 6. Export Eligibility

A match export is eligible only when:

* the match belongs to the current tournament
* the match is finalized
* the finalized match has complete official rows
* the existing export mapper can produce exactly the canonical 12 rows
* the current user/auth state permits export where Google Sheets export is used

Standings export is eligible only when:

* the tournament has finalized application data sufficient for standings export
* the standings snapshot comes from existing standings logic
* the export mapper can produce exactly the canonical 12 rows
* the official exported match count is valid under existing export rules
* the current user/auth state permits export where Google Sheets export is used

Draft, invalid, incomplete, or unfinalized data must not be exportable.

### 7. Export Status and Errors

The workflow should surface existing safe export states where supported.

Acceptable states include existing equivalents of:

* export ready
* export unavailable
* exporting
* exported
* already exported / duplicate suppressed
* export in progress
* retryable failure
* outcome uncertain
* verification failed
* authentication required
* network failure
* upstream timeout
* server configuration missing
* blocked because match is not finalized
* blocked because standings are unavailable

The implementation must not expose secrets, raw Google responses, raw Supabase JWTs, stack traces, spreadsheet IDs, worksheet names, updated ranges, service-account emails, private keys, access tokens, or environment variable values.

### 8. Idempotency and Verification

Android must not implement a second idempotency system.

Android must not generate client idempotency keys or retry tokens.

Android must not blindly retry uncertain exports.

Android must respect server responses for:

* successful export
* already successful replay
* export in progress
* retryable failure
* outcome uncertain
* verification failure

Android must not append again when the server indicates an identical export has already succeeded or is uncertain.

### 9. Same-Context Navigation

Export actions must preserve the same tournament context.

After export success or failure, the user should remain in or return to:

```text id="mcadnf"
TournamentDetailsDestination(tournamentId)
```

or:

```text id="jh130a"
MatchReviewDestination(tournamentId, matchId)
```

depending on where the export started.

The workflow must not return to tournament list unless no safe same-context destination exists.

### 10. Offline and Authentication Behavior

CSV export may be available offline if the required finalized local data and file/share capability are available.

Google Sheets export requires authentication and network availability through existing infrastructure.

If the user is not authenticated, Google Sheets export must be blocked or show the existing authentication-required state.

If offline, Google Sheets export must not pretend success.

Do not create a new background export queue unless one already exists and is in the approved boundary.

## Decisions

### Decision 1 — Existing export schemas remain authoritative

v0.11.6 must use existing CSV and Google Sheets export schemas.

No headers, columns, order, values, escaping, row counts, or schema version rules may be changed.

### Decision 2 — Finalized application data is the only export source

Draft rows, raw OCR evidence, unconfirmed corrections, and advisory team-matching suggestions must not be exported.

### Decision 3 — Google operations remain server-side

Android may call only the authenticated Supabase Edge Function.

Android must not call Google APIs, store Google credentials, request Google OAuth tokens, or expose Google metadata.

### Decision 4 — Existing Google Sheets request contracts remain frozen

Android must send only the approved request fields for `verify_connection`, `export_match`, and `export_standings`.

No client idempotency keys, retry tokens, operation IDs, force flags, or verification flags are authorized.

### Decision 5 — Server-side idempotency remains authoritative

Retry, duplicate suppression, uncertain-outcome handling, and verification remain server-side behavior.

Android must display or preserve those states safely, not duplicate them.

### Decision 6 — Export does not change scoring or standings

Export workflow must read existing finalized results and standings only.

It must not recalculate scoring differently in UI or export code.

### Decision 7 — Export does not perform finalization

Export actions must not finalize draft matches.

Finalization remains v0.11.4 behavior.

### Decision 8 — Export does not replace cloud sync

Cloud synchronization remains v0.11.5 behavior.

Export is an explicit user-facing output workflow, not the persistence/sync mechanism.

### Decision 9 — Same-context return is mandatory

After export success, failure, blocked state, or retryable state, the user must remain in the same tournament/match context.

## Acceptance Criteria

v0.11.6 is accepted when all of the following are true:

1. Export actions are available only from safe finalized/result contexts.
2. Exact tournament ID is preserved for every export.
3. Exact match ID is preserved for match export.
4. Finalized match results can be exported through existing CSV export behavior where available.
5. Tournament standings can be exported through existing CSV export behavior where available.
6. Finalized match results can be sent to the existing Google Sheets `export_match` operation where current infrastructure supports it.
7. Tournament standings can be sent to the existing Google Sheets `export_standings` operation where current infrastructure supports it.
8. Google Sheets connection verification can use the existing `verify_connection` operation where current infrastructure supports it.
9. Draft, incomplete, invalid, or unfinalized match data is not exportable.
10. Raw OCR evidence is not exportable.
11. Advisory team-matching suggestions are not exportable.
12. Existing CSV schemas remain unchanged.
13. Existing Google Sheets schemas remain unchanged.
14. Existing Google Sheets request contracts remain unchanged.
15. Android does not send idempotency keys, retry tokens, operation IDs, attempt values, force flags, or verification flags.
16. Android does not call Google OAuth or Google Sheets APIs directly.
17. Android does not store or expose Google credentials or spreadsheet metadata.
18. Existing server-side idempotency, retry, uncertain-outcome, and verification behavior remain authoritative.
19. Export success/failure states are surfaced safely where supported.
20. Same tournament/match context is preserved after export.
21. v0.11.0 tournament setup flow still works.
22. v0.11.1 manual match flow still works.
23. v0.11.2 screenshot/OCR review flow still works.
24. v0.11.3 team-matching advisory state remains safe.
25. v0.11.4 finalization and standings flow still works.
26. v0.11.5 cloud synchronization workflow remains intact.
27. No Supabase schema, RLS, RPC, Edge Function, Room schema, scoring, standings, OCR, screenshot, team matching, cloud sync, Gradle, schema, or visual redesign changes are introduced unless implementation proves a compile-blocking issue inside the approved boundary.

## Required Test Coverage

Implementation should add or update focused tests for export workflow integration.

### Unit tests

Where ViewModels, mappers, repositories, or export coordinators expose export behavior, test:

* finalized match export preserves exact tournament ID and match ID
* standings export preserves exact tournament ID
* draft match export is blocked
* incomplete match export is blocked
* unfinalized match export is blocked
* raw OCR evidence is not used as export source
* advisory team-matching suggestions are not used as export source
* CSV match export uses existing canonical export data
* CSV standings export uses existing canonical export data
* Google Sheets match export uses `operation = "export_match"`
* Google Sheets standings export uses `operation = "export_standings"`
* Google Sheets connection check uses `operation = "verify_connection"`
* Google Sheets request payload does not include unknown top-level fields
* Android does not supply idempotency keys, retry tokens, operation IDs, attempt values, force flags, or verification flags
* success state is surfaced safely
* retryable failure state is surfaced safely
* in-progress state is surfaced safely
* outcome-uncertain state is surfaced safely
* authentication-required state is surfaced safely
* export failure preserves same tournament/match context
* repeated identical successful export does not trigger a blind client append if server reports replay/duplicate suppression
* finalized protection remains intact after export

### Screen tests

Where export actions are visible, test:

* match export action is visible for finalized match where supported
* match export action is not visible or is disabled for draft match
* standings export action is visible when standings export is eligible
* CSV export action is visible where supported
* Google Sheets export action is visible only when supported by current state/auth infrastructure
* export progress/status is visible when UI state exposes it
* export success state is visible
* retryable failure/offline/authentication-required state is visible where supported
* outcome-uncertain state is visible where supported
* no secret, spreadsheet ID, worksheet name, updated range, token, service-account email, or raw upstream response is displayed
* user remains in same tournament/match context after export action

### Navigation / instrumentation tests

Add or update connected navigation coverage only where appropriate.

Preferred connected coverage:

```text id="k49blq"
confirmed tournament details
-> finalized match review or standings context
-> export action
-> export result/status
-> same tournament details / match review context
```

Also preserve existing connected navigation coverage for:

* v0.11.0 setup
* v0.11.1 manual match flow
* v0.11.2 OCR review navigation
* v0.11.4 finalization to standings context

Do not add Hilt infrastructure unless already required.

Use existing fake/in-memory repositories, existing fake export clients, existing ViewModel factories, and existing navigation-test wiring where possible.

## Implementation Constraints

Codex implementation must be constrained to the minimum files needed.

Expected likely areas:

* existing export ViewModel/state if present
* Tournament Details ViewModel/state/screen export actions
* Match Review ViewModel/state/screen export actions
* existing CSV export coordinator usage
* existing Google Sheets export client/coordinator usage
* focused unit tests
* focused screen tests
* focused navigation tests

The implementation prompt must list the exact approved file boundary before edits.

If implementation discovers that a required file is outside the approved boundary, it must stop and report the needed file instead of editing broadly.

## Verification Policy

Codex should run only lightweight verification unless explicitly instructed otherwise.

Codex-side check:

```text id="xojs34"
git diff --check
```

Local verification outside Codex should include:

```text id="6t20qk"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

Supabase local verification is required only if Supabase files are unexpectedly changed, which this version does not authorize.

No production Google Sheets write is allowed during development verification.

No production Supabase deployment is allowed during development verification.

## Non-Regression Requirements

The implementation must preserve:

* tournament management
* roster management
* manual match processing
* screenshot/OCR review navigation
* team-matching advisory behavior
* scoring
* standings
* finalization
* finalized-data protection
* protected correction workflow
* Room local persistence
* authentication/session restoration
* cloud sync
* conflict resolution
* export idempotency
* export verification
* existing Google Sheets Edge Function contracts
* existing CSV schemas
* existing navigation destinations
* existing test architecture

## Completion Definition

v0.11.6 is complete when:

1. The decision document is merged to `main`.
2. The implementation branch is created from updated `main`.
3. Export workflow integration is implemented or verified within approved scope.
4. Required tests are updated.
5. Local verification passes.
6. The implementation PR is merged.
7. `main` is synchronized with `origin/main`.
8. The working tree is clean.
