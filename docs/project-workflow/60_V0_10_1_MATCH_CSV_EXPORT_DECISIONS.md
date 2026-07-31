# v0.10.1 — Match CSV Export Decisions

## Purpose

v0.10.1 implements export generation for one finalized match using the stable match-result schema approved in v0.10.0.

This version creates deterministic CSV content for a finalized match. It does not implement Android file save/share behavior, Google Sheets export, retry behavior, or export verification beyond focused unit-level validation for match CSV generation.

## Canonical Scope

v0.10.1 includes:

- match CSV generation for one finalized match
- exactly 12 exported team rows
- finalized-only eligibility enforcement
- placement-ordered rows from 1 through 12
- stable v0.10.0 match-result column order
- UTF-8-safe string output
- RFC 4180-style field escaping
- deterministic header and line ordering
- unit tests for valid export, CSV escaping, finalized-only enforcement, and invalid finalized data rejection

## Out Of Scope

v0.10.1 does not implement:

- Android save/share UI
- file destination selection
- document provider integration
- Google Sheets Edge Function
- Google Sheets API calls
- Supabase export tables
- export retry/idempotency
- tournament standings CSV export
- export verification workflow
- OCR evidence export
- screenshot export
- new scoring formulas
- unrelated UI, Room, Supabase, OCR, correction, auth, or sync changes

## Approved Export Boundary

Match CSV generation must be implemented as domain/data export logic, not inside composables.

Decisions:

- The exporter should generate CSV text/content, not directly perform Android file writes.
- Android file creation and save/share behavior are deferred to v0.10.3 unless an existing utility boundary already supports safe file output without UI work.
- The exporter must be deterministic and unit-testable without Android device access.
- The exporter must not depend on Compose, Activity, Fragment, or Android UI state.
- Later UI integration may call this exporter, but this version does not add user-facing export flow.

## Required Schema

v0.10.1 must use the v0.10.0 match-result columns exactly:

1. `export_schema_version`
2. `export_type`
3. `tournament_id`
4. `tournament_name`
5. `match_id`
6. `match_label`
7. `match_finalized_at`
8. `row_number`
9. `placement`
10. `team_slot`
11. `team_name`
12. `player_1_name`
13. `player_2_name`
14. `player_3_name`
15. `player_4_name`
16. `placement_points`
17. `kills`
18. `kill_points`
19. `total_points`
20. `correction_status`

Required constant values:

- `export_schema_version` must be `phase_10_v1`.
- `export_type` must be `match_result`.

## Finalized-Only Rule

The exporter must accept only finalized official match data.

Decisions:

- Draft matches must be rejected.
- In-progress matches must be rejected.
- Invalid/unresolved OCR data must be rejected.
- Partially corrected OCR state must not be exported as official result data.
- Export must use finalized/corrected official application state.
- Export must fail closed with a typed/domain-level failure when the match is not finalized.

## Match Row Validation

A valid match CSV export requires:

- exactly 12 exported rows
- placements 1 through 12 present exactly once
- team slots 1 through 12 present at most once
- no duplicate placements
- no duplicate team slots
- required team identity available for every exported row
- required scoring values available for every exported row
- non-negative kill counts
- placement points from the approved position-points engine
- kill points from the approved kill-points engine
- total points from the approved match-total engine

The exporter must not silently repair invalid data.

## Match Row Ordering

Rows must be ordered by placement ascending:

1. placement 1
2. placement 2
3. placement 3
4. placement 4
5. placement 5
6. placement 6
7. placement 7
8. placement 8
9. placement 9
10. placement 10
11. placement 11
12. placement 12

`row_number` must reflect the exported row order and start at 1.

## Scoring Source

The exporter must reuse existing scoring engines.

Authoritative components:

- `PositionPointsEngine`
- `KillPointsEngine`
- `MatchTotalEngine`

Decisions:

- v0.10.1 must not create a new points table.
- v0.10.1 must not create a second scoring formula.
- If exported scoring values disagree with existing engines, export must fail or tests must catch the defect.
- Existing scoring behavior must remain unchanged.

## Team And Player Names

Decisions:

- `team_name` must come from the official tournament roster/team slot state.
- Player names must come from official roster/player state used by finalized result display.
- v0.10.1 exports `player_1_name` through `player_4_name`.
- If the roster allows more than four players, only the first four official display players are exported in v0.10.1 because the approved schema contains four player columns.
- This version must not redesign roster structure.
- This version must not export raw OCR player-name candidates.

## Match Label And Finalized Timestamp

Decisions:

- `match_label` should use existing match metadata if available.
- If no dedicated match label exists, implementation may derive a stable label from existing match number or match identity.
- `match_finalized_at` should use existing finalized timestamp if available.
- If no finalized timestamp currently exists, implementation must use the closest existing finalized-state timestamp or leave a documented deterministic blank value only if the current model has no safe source.
- The implementation must not introduce unrelated persistence migrations just to add a finalized timestamp in v0.10.1.

## Correction Status

`correction_status` must be a simple official-result indicator.

Allowed values:

- `original_finalized`
- `corrected_finalized`
- `unknown_finalized`

Decisions:

- Use `original_finalized` when the finalized result has no protected correction/correction workflow marker.
- Use `corrected_finalized` when existing official correction state makes that available.
- Use `unknown_finalized` only when the current finalized model does not expose a reliable correction marker.
- Do not export raw OCR evidence or private correction evidence.

## CSV Formatting

CSV output must follow v0.10.0 formatting:

- UTF-8-compatible text
- comma delimiter
- required header row
- deterministic line endings
- deterministic row order
- quote fields containing comma, quote, CR, LF, or leading/trailing whitespace
- escape quotes by doubling them
- preserve special characters in tournament names, team names, and player names

## Error Model

v0.10.1 should expose typed/domain-level failures suitable for later UI handling.

Expected failure categories:

- match not finalized
- missing placement
- duplicate placement
- duplicate team slot
- missing team identity
- invalid team slot
- invalid kill count
- invalid row count
- scoring mismatch or unavailable scoring data, if applicable

The exact Kotlin type names may follow existing project conventions.

## Tests Required

Add or update focused unit tests for:

- successful finalized match CSV export with 12 rows
- exact header column order
- placement-ascending row order
- `row_number` starts at 1 and increments
- draft/non-finalized match is rejected
- missing placement is rejected
- duplicate placement is rejected
- duplicate team slot is rejected
- CSV escaping for commas, quotes, newlines, and leading/trailing whitespace
- special characters are preserved
- scoring fields match existing scoring engines
- no raw OCR evidence fields appear in output

## Verification

Codex-side verification should be lightweight:

- `git diff --check`

Local verification after Codex implementation should include:

- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat lintDebug`
- `.\gradlew.bat assembleDebug`
- `git diff --check`

Connected-device tests are not required for v0.10.1 unless implementation unexpectedly changes UI/device behavior.

## Acceptance Criteria

v0.10.1 is complete when:

- finalized match CSV generation exists
- the v0.10.0 match-result schema is preserved exactly
- non-finalized matches cannot be exported
- invalid finalized data is rejected
- CSV escaping is deterministic and tested
- scoring values come from existing scoring engines
- unit tests cover success and failure paths
- no Android save/share UI is added
- no Google Sheets code is added
- no Supabase Edge Function is added
- no unrelated behavior is changed
- `git diff --check` passes
