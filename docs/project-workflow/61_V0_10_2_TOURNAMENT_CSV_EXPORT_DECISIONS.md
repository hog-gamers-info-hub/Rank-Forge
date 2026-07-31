# v0.10.2 — Tournament CSV Export Decisions

## Purpose

v0.10.2 defines the implementation contract for exporting cumulative tournament standings as deterministic CSV content.

This version builds on the stable export data model approved in v0.10.0 and the match CSV boundary implemented in v0.10.1.

v0.10.2 must produce official tournament standings rows from finalized match data only. It must preserve existing scoring, standings, and tie-break behavior.

## Canonical Scope

v0.10.2 includes:

- tournament standings CSV generation for one tournament
- finalized-match-only inclusion
- cumulative standings rows for the twelve fixed tournament slots
- exact v0.10.0 tournament-standings column order
- existing scoring-engine reuse
- existing standings-order reuse
- existing tie-break-order reuse
- deterministic CSV header and row ordering
- UTF-8-safe string output
- RFC 4180-style field escaping
- typed/domain-level validation failures
- focused unit tests for success, validation failures, schema order, row order, escaping, scoring totals, and finalized-only behavior

## Out Of Scope

v0.10.2 does not implement:

- Android save/share UI
- file destination selection
- document provider integration
- Google Sheets Edge Function
- Google Sheets API calls
- Supabase export tables
- export retry/idempotency
- match CSV schema changes
- Android navigation changes
- Android export screen
- OCR evidence export
- screenshot export
- new scoring formulas
- new tie-break formulas
- Room migrations
- Supabase migrations
- Gradle dependency changes
- unrelated UI, Room, Supabase, OCR, correction, auth, sync, or architecture changes

## Approved Export Boundary

Tournament CSV generation must be implemented as Android-independent domain/data export logic.

Decisions:

- The exporter should generate CSV text/content.
- The exporter must not directly write files.
- The exporter must not use Android `ContentResolver`.
- The exporter must not use Compose, Activity, Fragment, or Android UI state.
- Android file save/share behavior remains deferred to v0.10.3.
- Google Sheets export remains deferred to v0.10.4 and later.
- The exporter must be deterministic and unit-testable through JVM tests.

## Required Schema

v0.10.2 must use the v0.10.0 tournament-standings columns exactly:

1. `export_schema_version`
2. `export_type`
3. `tournament_id`
4. `tournament_name`
5. `exported_match_count`
6. `standings_rank`
7. `team_slot`
8. `team_name`
9. `player_1_name`
10. `player_2_name`
11. `player_3_name`
12. `player_4_name`
13. `matches_played`
14. `total_position_points`
15. `total_kills`
16. `total_kill_points`
17. `total_points`
18. `best_placement`
19. `first_place_count`
20. `tie_break_status`

Required constant values:

- `export_schema_version` must be `phase_10_v1`.
- `export_type` must be `tournament_standings`.

## Finalized-Match Inclusion Rule

Tournament standings export must include finalized match data only.

Decisions:

- Draft matches must be excluded.
- Invalid/unresolved OCR data must be excluded.
- Partially corrected OCR state must not be exported as official result data.
- Corrected finalized data is official and may be included.
- Export must use finalized application match state, not raw OCR extraction state.
- Export must require at least one finalized match.
- If no finalized matches exist, export must fail closed with a typed/domain-level failure.
- Export must not mutate or repair match data during export.

## Tournament Identity Rule

All exported data must belong to the requested tournament.

Decisions:

- `Tournament.id` is the export tournament identity.
- Included matches must have `match.tournamentId == tournament.id`.
- Included team slots must have `teamSlot.tournamentId == tournament.id`.
- Included roster players must have `rosterPlayer.tournamentId == tournament.id`.
- Mismatched tournament IDs must block export.
- The exporter must not silently drop mismatched records if doing so would hide invalid input.

## Required Row Count

A valid tournament standings export must produce exactly 12 standings rows.

Decisions:

- One row must exist for each fixed tournament slot from 1 through 12.
- Missing team slots must block export.
- Duplicate team slots must block export.
- Blank team names must block export.
- A team with zero finalized participation should not normally exist because every finalized match requires all 12 teams. If encountered, export must fail rather than inventing totals.

## Finalized Match Validation

Every finalized match included in the tournament standings export must satisfy the official match-result integrity rules.

Each finalized match must have:

- exactly 12 placement entries
- exactly 12 kill entries
- placements 1 through 12 present exactly once
- team slots 1 through 12 present exactly once in placements
- team slots 1 through 12 present exactly once in kills
- placements and kills referring to the same 12 team slots
- no duplicate placements
- no duplicate team slots
- no negative kill values
- no out-of-range team slots
- no mismatched tournament IDs

Invalid finalized match data must block tournament export.

The exporter must not silently repair invalid finalized data.

## Standings Source

v0.10.2 must preserve existing application standings behavior.

Authoritative existing components:

- `PositionPointsEngine`
- `KillPointsEngine`
- `MatchTotalEngine`
- `CumulativeTournamentStandingsEngine`
- `TieBreakRules`

Decisions:

- v0.10.2 must not create a new placement points table.
- v0.10.2 must not create a new kill-points formula.
- v0.10.2 must not create a new match-total formula.
- v0.10.2 must not create a separate standings sort.
- Existing standings and tie-break order must remain authoritative.
- If implementation needs aggregate values that existing standings output does not expose, it may derive those values from finalized match data using the existing scoring engines, but it must not change the ordering produced by existing standings logic.
- Any disagreement between exported totals and existing scoring/standings behavior must be treated as a defect.

## Tournament Standings Row Ordering

Export rows must follow the same order as the existing application standings logic.

Decisions:

- Use existing standings/tie-break output order as the export order.
- `standings_rank` is the one-based row rank in that existing application standings order.
- Do not use database insertion order.
- Do not use team-slot order unless the existing standings/tie-break logic returns that order for a complete unresolved tie.
- Do not create export-only ranking behavior.
- Complete unresolved tie behavior must follow the current app standings behavior.

## Field Mapping

Map fields as follows:

- `tournament_id` = `Tournament.id`
- `tournament_name` = `Tournament.name`
- `exported_match_count` = count of finalized matches included in the export
- `standings_rank` = one-based rank/order from existing standings output
- `team_slot` = fixed team slot number
- `team_name` = matching `TeamSlot.teamName`
- `player_1_name` through `player_4_name` = first four matching `RosterPlayer.displayName` values in supplied roster order
- if fewer than four official roster players are supplied, leave remaining player columns empty
- `matches_played` = finalized matches included for that team
- `total_position_points` = cumulative placement points from finalized matches
- `total_kills` = cumulative kills from finalized matches
- `total_kill_points` = cumulative kill points from finalized matches
- `total_points` = cumulative official total
- `best_placement` = best/lower numeric placement across finalized matches
- `first_place_count` = number of finalized first-place finishes
- `tie_break_status` = simple indicator derived from existing standings/tie-break behavior

## Tie Break Status

`tie_break_status` must be simple and deterministic.

Allowed values:

- `unique_order`
- `tie_break_applied`
- `unresolved_tie`
- `resolved_by_existing_order`

Decisions:

- Use `unique_order` when no tie affects the row's exported order.
- Use `tie_break_applied` when existing tie-break logic resolved ordering between tied teams.
- Use `unresolved_tie` when existing standings logic identifies a complete unresolved tie.
- Use `resolved_by_existing_order` when the current existing engine does not expose enough per-row tie metadata, but the export order still follows existing standings output.
- Do not add new tie-break criteria for export.
- Do not export private debugging metadata to explain tie resolution.

## CSV Formatting

CSV output must follow the Phase 10 CSV policy:

- UTF-8-compatible text
- comma delimiter
- required header row
- CRLF line endings
- deterministic row order
- no trailing extra blank record
- quote fields containing comma, quote, CR, LF, leading whitespace, or trailing whitespace
- escape embedded double quotes by doubling them
- preserve Unicode and special characters in tournament names, team names, and player names
- do not add a UTF-8 BOM because this version returns Kotlin string content rather than file bytes

## Error Model

v0.10.2 should expose typed/domain-level failures suitable for later UI handling.

Expected failure categories:

- no finalized matches
- tournament identity mismatch
- invalid finalized match
- invalid finalized match row count
- missing placement
- duplicate placement
- duplicate team slot
- missing team slot
- missing team identity
- invalid team slot
- missing kill value
- invalid kill count
- duplicate match identity
- standings generation failure, if applicable

The exact Kotlin type names may follow existing project conventions.

## Tests Required

Add or update focused unit tests for:

- successful tournament standings CSV export
- exact header column order
- exactly 12 standings data rows
- finalized-match-only inclusion
- draft matches excluded
- no-finalized-match rejection
- tournament identity mismatch rejection
- invalid finalized match rejection
- duplicate placement rejection
- duplicate team-slot rejection
- missing kill rejection
- negative kill rejection
- blank team-name rejection
- standings row order follows existing standings/tie-break behavior
- `standings_rank` starts at 1 and increments in exported order
- `exported_match_count` is correct
- cumulative position points are correct
- cumulative kills are correct
- cumulative kill points are correct
- cumulative total points are correct
- best placement is correct
- first-place count is correct
- player columns use first four official roster players only
- CSV escaping for commas, quotes, newlines, and leading/trailing whitespace
- Unicode and special characters are preserved
- raw OCR/private evidence column names do not appear in output

Use synthetic data only.

## Verification

Codex-side verification should remain lightweight:

- `git diff --check`

Local verification after Codex implementation should include:

- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat lintDebug`
- `.\gradlew.bat assembleDebug`
- `git diff --check`

Connected-device tests are not required for v0.10.2 unless implementation unexpectedly changes UI, navigation, Room, manifest, lifecycle, or Android device behavior.

## Acceptance Criteria

v0.10.2 is complete when:

- finalized tournament standings CSV generation exists
- the v0.10.0 tournament-standings schema is preserved exactly
- only finalized matches contribute to exported standings
- draft matches are excluded
- no-finalized-match export fails safely
- invalid finalized data is rejected
- exactly 12 standings rows are exported for valid data
- row ordering follows existing application standings/tie-break behavior
- cumulative scoring values come from existing scoring behavior
- CSV escaping is deterministic and tested
- unit tests cover success and failure paths
- no Android save/share UI is added
- no Google Sheets code is added
- no Supabase Edge Function is added
- no Room migration is added
- no unrelated behavior is changed
- `git diff --check` passes
