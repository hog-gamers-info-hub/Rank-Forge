# v0.10.0 — Export Data Model Decisions

## Purpose

v0.10.0 defines the stable export data model for Phase 10 CSV and Google Sheets exports.

This document freezes the official export schema before implementation starts so later versions can implement CSV files, Google Sheets export, retry behavior, and export verification without inventing column names, ordering, identifiers, or finalized-result rules during code changes.

This version is a decision-only version. It does not implement export behavior.

## Canonical Scope

v0.10.0 defines:

- stable match-result export columns
- stable tournament-standings export columns
- column ordering
- required identifiers
- finalized-only export eligibility
- row ordering
- tie display policy
- CSV formatting baseline
- Google Sheets compatibility baseline
- private/internal fields excluded from official exports
- validation principles for later implementation

## Out Of Scope

v0.10.0 does not implement:

- CSV file generation
- Android share/save UI
- Android export screens
- Android file picker or document destination behavior
- Google Sheets Edge Functions
- Google Sheets API calls
- Google credential handling
- export retry or idempotency implementation
- export operation persistence
- Room export tables
- Supabase export tables
- score recalculation redesign
- OCR evidence export
- screenshot export
- screenshot file export
- unrelated database, sync, auth, OCR, scoring, correction, or architecture changes

## Finalized-Only Export Rule

Official Phase 10 exports must use finalized application results only.

Decisions:

- Only finalized match data is eligible for official export.
- Draft matches must not be exported as official results.
- In-progress matches must not be exported as official results.
- Invalid matches must not be exported as official results.
- Partially corrected OCR output must not be exported as official results.
- Unresolved OCR/team-matching data must not be exported as official results.
- Export code must read from finalized application state, not raw OCR extraction state.
- Exports must not bypass finalized-match protection rules.
- Exports must fail safely when requested data is not finalized.
- Failure must be structured enough for later UI and verification work to explain why export was blocked.

## Source Of Truth

Existing finalized app data is the source of truth for export.

Phase 10 must reuse the existing scoring and standings behavior. It must not introduce alternate formulas or a second scoring path.

Authoritative existing components:

- `PositionPointsEngine`
- `KillPointsEngine`
- `MatchTotalEngine`
- `CumulativeTournamentStandingsEngine`
- `TieBreakRules`
- `ScoringVerificationEngine`, where applicable
- finalized match state produced by existing finalization workflows
- corrected official result state after approved correction workflows

Decisions:

- Position points remain the approved 1–12 placement table.
- One kill remains one point.
- Match total remains placement points plus kill points.
- Tournament total remains the sum of finalized match totals.
- Export verification must compare exported values against finalized application totals.
- Phase 10 must not silently repair scores, placements, team slots, or names during export.

## Identifier Policy

Official exports need traceability without exposing private auth or implementation details.

Allowed export identifiers:

- `tournament_id`
- `match_id` for match-level rows
- `team_slot`

Required human-readable fields:

- `tournament_name`
- `match_label`
- `team_name`
- player names where applicable

Private identifiers must not be exported.

Excluded identifiers:

- Supabase auth user IDs
- access tokens
- refresh tokens
- Google credential identifiers
- local file paths
- internal sync queue payload IDs
- private screenshot storage paths
- raw OCR evidence IDs unless separately approved in a later decision

Implementation note:

If the current model does not expose a dedicated `match_label`, later implementation may derive a stable display label from existing match metadata without changing the export schema.

## Match CSV Export Columns

A finalized match export must use this exact column order:

| Order | Column |
|---:|---|
| 1 | `export_schema_version` |
| 2 | `export_type` |
| 3 | `tournament_id` |
| 4 | `tournament_name` |
| 5 | `match_id` |
| 6 | `match_label` |
| 7 | `match_finalized_at` |
| 8 | `row_number` |
| 9 | `placement` |
| 10 | `team_slot` |
| 11 | `team_name` |
| 12 | `player_1_name` |
| 13 | `player_2_name` |
| 14 | `player_3_name` |
| 15 | `player_4_name` |
| 16 | `placement_points` |
| 17 | `kills` |
| 18 | `kill_points` |
| 19 | `total_points` |
| 20 | `correction_status` |

Column decisions:

- `export_schema_version` must be `phase_10_v1`.
- `export_type` must be `match_result`.
- `row_number` is the exported row position, starting at 1.
- `placement` is the finalized placement from 1 through 12.
- `team_slot` is the fixed tournament slot from 1 through 12.
- `team_name` comes from the finalized roster/team slot state.
- `player_1_name` through `player_4_name` come from official roster/player state used for finalized result display.
- `placement_points` must come from the approved position-points engine.
- `kills` must be the finalized team kill count.
- `kill_points` must come from the approved kill-points engine.
- `total_points` must come from the approved match-total engine.
- `correction_status` must be a simple official-result indicator derived from finalized/correction state, not raw OCR evidence.

Validity decisions:

- A valid finalized match export must contain exactly 12 team rows.
- Missing placements must block export.
- Duplicate placements must block export.
- Duplicate team slots must block export.
- Missing finalized team identity must block export.
- Missing required scoring values must block export.

## Match Row Ordering

Match CSV rows must be ordered by placement ascending:

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

Decisions:

- Export order must be deterministic.
- Export order must not depend on database insertion order.
- Export order must not depend on UI rendering side effects.
- If finalized data is missing any placement from 1 through 12, export must fail verification instead of silently exporting incomplete data.
- If finalized data contains duplicate placements or duplicate team slots, export must fail verification.

## Tournament Standings CSV Export Columns

A cumulative tournament standings export must use this exact column order:

| Order | Column |
|---:|---|
| 1 | `export_schema_version` |
| 2 | `export_type` |
| 3 | `tournament_id` |
| 4 | `tournament_name` |
| 5 | `exported_match_count` |
| 6 | `standings_rank` |
| 7 | `team_slot` |
| 8 | `team_name` |
| 9 | `player_1_name` |
| 10 | `player_2_name` |
| 11 | `player_3_name` |
| 12 | `player_4_name` |
| 13 | `matches_played` |
| 14 | `total_position_points` |
| 15 | `total_kills` |
| 16 | `total_kill_points` |
| 17 | `total_points` |
| 18 | `best_placement` |
| 19 | `first_place_count` |
| 20 | `tie_break_status` |

Column decisions:

- `export_schema_version` must be `phase_10_v1`.
- `export_type` must be `tournament_standings`.
- `exported_match_count` is the number of finalized matches included in the cumulative export.
- `standings_rank` must follow existing standings logic.
- `team_slot` is the fixed tournament slot from 1 through 12.
- `team_name` comes from official tournament roster/team slot state.
- `matches_played` counts finalized matches included for that team.
- `total_position_points` is the cumulative placement-points total from finalized matches.
- `total_kills` is the cumulative kill count from finalized matches.
- `total_kill_points` is the cumulative kill-points total from finalized matches.
- `total_points` is the cumulative official total.
- `best_placement` is the best finalized placement included in the standings.
- `first_place_count` is the finalized first-place count used by tie-break logic.
- `tie_break_status` identifies whether the exported ranking is uniquely ordered or uses an existing fallback order.

Validity decisions:

- Only finalized matches may contribute to tournament standings export.
- Draft matches must be excluded.
- Invalid/unresolved OCR data must be excluded.
- Standings export must not create a separate sort from the application standings logic.
- Export must fail or clearly report a validation error if finalized standings cannot be generated consistently.

## Tournament Standings Row Ordering

Tournament standings export rows must follow the same order as the application standings logic.

Decisions:

- Existing `CumulativeTournamentStandingsEngine` and `TieBreakRules` behavior remains authoritative.
- Phase 10 must not create a separate standings sort.
- Tie-break ordering must be preserved.
- If complete tie metadata is not currently exposed by existing engines, later implementation may use `resolved_by_existing_order` or equivalent as `tie_break_status` without changing scoring logic.
- Any later enhancement to expose richer tie metadata must preserve this export schema unless a future schema version is explicitly approved.

## CSV Formatting Policy

CSV output must be deterministic and compatible with spreadsheet tools.

Decisions:

- CSV encoding must be UTF-8.
- CSV delimiter must be comma.
- Header row is required.
- Column order is stable and must not change silently in later versions.
- CSV must use standard RFC 4180-style quoting behavior:
  - quote fields containing comma
  - quote fields containing quote
  - quote fields containing carriage return
  - quote fields containing line feed
  - quote fields containing leading or trailing whitespace
  - escape quotes by doubling them
- CSV line endings must be consistent and deterministic.
- Generated CSV must preserve special characters in tournament names, team names, and player names.
- Later file-validation work must verify UTF-8 integrity.

## Google Sheets Compatibility

Google Sheets exports must use the same logical data schema as CSV exports.

Decisions:

- Google Sheets match export must use the same logical column order as match CSV export.
- Google Sheets standings export must use the same logical column order as tournament standings CSV export.
- Google Sheets-specific formatting is out of scope for v0.10.0.
- v0.10.4 and later may add server-side formatting only if it does not change the stable data schema.
- Android must never call Google Sheets APIs directly.
- Google Sheets updates must run through a secure Supabase Edge Function.
- Google credentials must never be stored in Android source, Android resources, the APK, or committed repository files.

## Excluded Fields

Official Phase 10 exports must exclude:

- raw OCR text
- OCR bounding boxes
- OCR confidence metadata
- screenshot file paths
- screenshot storage paths
- screenshot hashes
- private correction evidence
- preserved OCR evidence payloads
- Supabase auth user IDs
- access tokens
- refresh tokens
- Google credentials
- service-account JSON
- local database implementation details
- local file paths
- debug logs
- internal sync queue payloads
- private storage references
- secrets of any kind

Decision:

If a later version needs an evidence/debug export, it must be separately approved and must not be mixed with official Phase 10 result exports.

## Error And Validation Principles

Phase 10 exports must fail closed.

Decisions:

- Export must not silently export incomplete official results.
- Export must not silently repair missing teams.
- Export must not silently repair duplicate teams.
- Export must not silently repair duplicate placements.
- Export must not recalculate using a different scoring model.
- Export must not export unfinalized OCR guesses.
- Export must report validation failure clearly.
- Later UI work may translate structured export failures into operator-friendly messages.
- Later verification work must compare generated rows and totals against finalized application data.

## Later Version Handoff

### v0.10.1 — Match CSV Export

v0.10.1 must implement match CSV generation using the match-result schema defined in this document.

It must not alter column names or ordering.

### v0.10.2 — Tournament CSV Export

v0.10.2 must implement cumulative standings CSV generation using the tournament-standings schema defined in this document.

It must preserve existing standings order.

### v0.10.3 — UTF-8 and File Validation

v0.10.3 must verify UTF-8 preservation, deterministic file contents, and generated CSV integrity.

It may define Android file destination behavior if required by that version, but it must not change the export schema.

### v0.10.4 — Google Sheets Edge Function

v0.10.4 must create the secure server-side Sheets integration.

It must preserve the schema defined here and must not expose Google credentials to Android.

### v0.10.5 — Google Sheets Match Export

v0.10.5 must export finalized match rows using the match-result schema defined here.

### v0.10.6 — Google Sheets Standings Export

v0.10.6 must export cumulative standings rows using the tournament-standings schema defined here.

### v0.10.7 — Export Retry and Idempotency

v0.10.7 must prevent duplicate exported rows without changing official export columns.

Retry/idempotency metadata may exist outside the official export rows unless separately approved.

### v0.10.8 — Export Verification

v0.10.8 must compare exported rows, ordering, and totals against finalized application data and the schema in this document.

## Acceptance Criteria

v0.10.0 is complete when:

- exact match export columns are documented
- exact tournament standings export columns are documented
- match row ordering is documented
- tournament standings row ordering is documented
- finalized-only export rule is documented
- source-of-truth policy is documented
- identifier policy is documented
- private/excluded fields are documented
- CSV formatting baseline is documented
- Google Sheets compatibility rule is documented
- later-version handoff is documented
- no source code is changed
- no tests are changed
- no Supabase files are changed
- `git diff --check` passes
